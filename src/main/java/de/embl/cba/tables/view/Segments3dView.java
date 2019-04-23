package de.embl.cba.tables.view;

import bdv.viewer.Source;
import customnode.CustomTriangleMesh;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.objects3d.FloodFill;
import de.embl.cba.tables.ij3d.AnimatedViewAdjuster;
import de.embl.cba.tables.mesh.MeshExtractor;
import de.embl.cba.tables.mesh.MeshUtils;
import de.embl.cba.tables.color.*;
import de.embl.cba.tables.image.ImageSourcesModel;
import de.embl.cba.tables.imagesegment.ImageSegment;
import de.embl.cba.tables.select.SelectionListener;
import de.embl.cba.tables.select.SelectionModel;
import ij3d.Content;
import ij3d.Image3DUniverse;
import ij3d.UniverseListener;
import isosurface.MeshEditor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.java3d.View;
import org.scijava.vecmath.Color3f;

import java.util.*;

public class Segments3dView < T extends ImageSegment >
{
	private final List< T > segments;
	private final SelectionModel< T > selectionModel;
	private final SelectionColoringModel< T > selectionColoringModel;
	private final ImageSourcesModel imageSourcesModel;


	private Image3DUniverse universe;
	private T recentFocus;
	private double voxelSpacing3DView;
	private HashMap< T, CustomTriangleMesh > segmentToMesh;
	private HashMap< T, Content > segmentToContent;
	private HashMap< Content, T > contentToSegment;
	private double transparency;
	private boolean isListeningToUniverse;
	private int meshSmoothingIterations;
	private int segmentFocusAnimationDurationMillis;

	public Segments3dView(
			final List< T > segments,
			final SelectionModel< T > selectionModel,
			final SelectionColoringModel< T > selectionColoringModel,
			ImageSourcesModel imageSourcesModel )
	{

		this( segments,
				selectionModel,
				selectionColoringModel,
				imageSourcesModel,
				null );
	}

	public Segments3dView(
			final List< T > segments,
			final SelectionModel< T > selectionModel,
			final SelectionColoringModel< T > selectionColoringModel,
			ImageSourcesModel imageSourcesModel,
			Image3DUniverse universe )
	{
		this.segments = segments;
		this.selectionModel = selectionModel;
		this.selectionColoringModel = selectionColoringModel;
		this.imageSourcesModel = imageSourcesModel;
		this.universe = universe;

		this.transparency = 0.5;
		this.voxelSpacing3DView = 0.1;
		this.meshSmoothingIterations = 5;
		this.segmentFocusAnimationDurationMillis = 750;

		this.segmentToMesh = new HashMap<>();
		this.segmentToContent = new HashMap<>();
		this.contentToSegment = new HashMap<>();

		registerAsSelectionListener( this.selectionModel );
		registerAsColoringListener( this.selectionColoringModel );
	}

	public void setVoxelSpacing3DView( double voxelSpacing3DView )
	{
		this.voxelSpacing3DView = voxelSpacing3DView;
	}

	public void setTransparency( double transparency )
	{
		this.transparency = transparency;
	}

	public void setMeshSmoothingIterations( int iterations )
	{
		this.meshSmoothingIterations = iterations;
	}

	public void setSegmentFocusAnimationDurationMillis( int duration )
	{
		this.segmentFocusAnimationDurationMillis = duration;
	}

	public Image3DUniverse getUniverse()
	{
		return universe;
	}

	private ArrayList< double[] > getVoxelSpacings( Source< ? > labelsSource )
	{
		final ArrayList< double[] > calibrations = new ArrayList<>();
		final int numMipmapLevels = labelsSource.getNumMipmapLevels();
		for ( int level = 0; level < numMipmapLevels; ++level )
			calibrations.add( BdvUtils.getCalibration( labelsSource, level ) );

		return calibrations;
	}


	private void registerAsColoringListener( ColoringModel< T > coloringModel )
	{
		coloringModel.listeners().add( () -> adaptSegmentColors() );
	}

	private void adaptSegmentColors()
	{
		for ( T segment : segmentToContent.keySet() )
		{
			final Color3f color3f = getColor3f( segment );
			final Content content = segmentToContent.get( segment );
			content.setColor( color3f );
		}
	}

	public void registerAsSelectionListener( SelectionModel< T > selectionModel )
	{
		selectionModel.listeners().add( new SelectionListener< T >()
		{
			@Override
			public synchronized void selectionChanged()
			{
				final Set< T > selected = selectionModel.getSelected();

				addSelectedSegments( selected );
				removeUnselectedSegments( selected );
			}

			@Override
			public synchronized void focusEvent( T selection )
			{
				if ( selection == recentFocus ) return;

				recentFocus = selection;

				if ( segmentToContent.containsKey( selection ) )
				{
					final AnimatedViewAdjuster adjuster =
							new AnimatedViewAdjuster(
									universe,
									AnimatedViewAdjuster.ADJUST_BOTH );
					adjuster.add( segmentToContent.get( selection )  );

					adjuster.apply(
							30,
							segmentFocusAnimationDurationMillis,
							0.8 );
				}
			}
		} );


	}

	public void removeUnselectedSegments( Set< T > selectedSegments )
	{
		final Set< T > currentSegments = segmentToContent.keySet();
		final Set< T > remove = new HashSet<>();
		for ( T segment : currentSegments )
			if ( ! selectedSegments.contains( segment ) )
				remove.add( segment );

		for( T segment : remove )
			removeSegmentFrom3DView( segment );
	}

	public void addSelectedSegments( Set< T > segments )
	{
		for ( T segment : segments )
			if ( ! segmentToContent.containsKey( segment ) )
				addSegmentTo3DView( segment );
	}

	private synchronized void removeSegmentFrom3DView( T segment )
	{
		final Content content = segmentToContent.get( segment );
		universe.removeContent( content.getName() );
		segmentToContent.remove( segment );
		contentToSegment.remove( content );
	}

	private synchronized void addSegmentTo3DView( T segment )
	{
		CustomTriangleMesh triangleMesh = getTriangleMesh( segment );
		if ( triangleMesh == null ) return;
		MeshEditor.smooth2(triangleMesh, meshSmoothingIterations );
		addMeshToUniverse( segment, triangleMesh );
	}

	private CustomTriangleMesh getTriangleMesh( T segment )
	{
		if ( segment.getMesh() == null )
			addMesh( segment );

		CustomTriangleMesh triangleMesh = MeshUtils.asCustomTriangleMesh( segment.getMesh() );
		triangleMesh.setColor( getColor3f( segment ) );
		return triangleMesh;
	}

	private void addMesh( ImageSegment segment )
	{

		final Source< ? > labelsSource =
				imageSourcesModel.sources().get( segment.imageId() ).source();

		final int level = getLevel( getVoxelSpacings( labelsSource ), voxelSpacing3DView );
		final double[] voxelSpacing = getVoxelSpacings( labelsSource ).get( level );

		final RandomAccessibleInterval< ? extends RealType< ? > > labelsRAI =
				getLabelsRAI( segment, level );

		if ( segment.boundingBox() == null )
			setSegmentBoundingBox( segment, labelsRAI, voxelSpacing );

		FinalInterval boundingBox =
				getVoxelInterval( segment.boundingBox(), voxelSpacing );

		final MeshExtractor meshExtractor = new MeshExtractor(
				Views.extendZero( ( RandomAccessibleInterval ) labelsRAI ),
				boundingBox,
				new AffineTransform3D(),
				new int[]{ 1, 1, 1 },
				() -> false );

		final float[] meshCoordinates = meshExtractor.generateMesh( segment.labelId() );

		segment.setMesh( meshCoordinates );
	}

	private void setSegmentBoundingBox(
			ImageSegment segment,
			RandomAccessibleInterval< ? extends RealType< ? > > labelsRAI,
			double[] voxelSpacing )
	{
		final long[] voxelCoordinate =
				getSegmentVoxelCoordinate( segment, voxelSpacing );

		final FloodFill floodFill = new FloodFill(
				labelsRAI,
				new DiamondShape( 1 ),
				1000 * 1000 * 1000L );

		floodFill.run( voxelCoordinate );
		final RandomAccessibleInterval mask = floodFill.getCroppedRegionMask();

		final int numDimensions = segment.numDimensions();
		final double[] min = new double[ numDimensions ];
		final double[] max = new double[ numDimensions ];
		for ( int d = 0; d < numDimensions; d++ )
		{
			min[ d ] = mask.min( d ) * voxelSpacing[ d ];
			max[ d ] = mask.max( d ) * voxelSpacing[ d ];
		}

		segment.setBoundingBox( new FinalRealInterval( min, max ));
	}

	private long[] getSegmentVoxelCoordinate( ImageSegment segment, double[] calibration )
	{
		final long[] voxelCoordinate = new long[ segment.numDimensions() ];
		for ( int d = 0; d < segment.numDimensions(); d++ )
			voxelCoordinate[ d ] = ( long ) (
					segment.getDoublePosition( d ) / calibration[ d ] );
		return voxelCoordinate;
	}

	private FinalInterval getVoxelInterval(
			FinalRealInterval realInterval, double[] calibration )
	{
		final long[] min = new long[ 3 ];
		final long[] max = new long[ 3 ];
		for ( int d = 0; d < 3; d++ )
		{
			min[ d ] = (long) ( realInterval.realMin( d )
					/ calibration[ d ] );
			max[ d ] = (long) ( realInterval.realMax( d )
					/ calibration[ d ] );
		}
		return new FinalInterval( min, max );
	}

	private RandomAccessibleInterval< ? extends RealType< ? > >
	getLabelsRAI( ImageSegment segment, int level )
	{
		final Source< ? > labelsSource
				= imageSourcesModel.sources().get( segment.imageId() ).source();

		final RandomAccessibleInterval< ? extends RealType< ? > > rai =
				BdvUtils.getRealTypeNonVolatileRandomAccessibleInterval(
						labelsSource, 0, level );

		return rai;
	}


	private void addMeshToUniverse( T segment, CustomTriangleMesh mesh )
	{
		initUniverseAndListener();

		// TODO: is the viewer transform altered here?
		final Content content =
				universe.addCustomMesh( mesh, "" + segment.labelId() );

		content.setTransparency( (float) transparency );
		content.setLocked( true );
		//universe.adjustView( content );

		segmentToContent.put( segment, content );
		contentToSegment.put( content, segment );
	}

	private void initUniverseAndListener()
	{
		if ( universe == null )
			universe = new Image3DUniverse();

		if ( universe.getWindow() == null )
		{
			universe.show();
			universe.getWindow().setResizable( false );
		}

		if ( ! isListeningToUniverse )
			isListeningToUniverse = addUniverseListener();
	}

	private boolean addUniverseListener()
	{
		universe.addUniverseListener( new UniverseListener()
		{

			@Override
			public void transformationStarted( View view )
			{

			}

			@Override
			public void transformationUpdated( View view )
			{

				// TODO maybe try to synch this with the Bdv View

				//				final Transform3D transform3D = new Transform3D();
//			view.getUserHeadToVworld( transform3D );

//				final Transform3D transform3D = new Transform3D();
//			universe.getVworldToCamera( transform3D );
//				System.out.println( transform3D );

//				final Transform3D transform3DInverse = new Transform3D();
//				universe.getVworldToCameraInverse( transform3DInverse );
//				System.out.println( transform3DInverse );

//				final TransformGroup transformGroup =
//						universe.getViewingPlatform()
//								.getMultiTransformGroup().getTransformGroup(
//										DefaultUniverse.ZOOM_TG );
//
//				final Transform3D transform3D = new Transform3D();
//				transformGroup.getTransform( transform3D );
//
//				System.out.println( transform3D );
			}

			@Override
			public void transformationFinished( View view )
			{

			}

			@Override
			public void contentAdded( Content c )
			{

			}

			@Override
			public void contentRemoved( Content c )
			{

			}

			@Override
			public void contentChanged( Content c )
			{

			}

			@Override
			public void contentSelected( Content c )
			{
				if ( ! contentToSegment.containsKey( c ) )
					return;

				final T segment = contentToSegment.get( c );

				if ( selectionModel.isFocused( segment ) )
					return;
				else
					selectionModel.focus( segment );


			}

			@Override
			public void canvasResized()
			{

			}

			@Override
			public void universeClosed()
			{

			}
		} );

		return true;
	}

	private Color3f getColor3f( T imageSegment )
	{
		final ARGBType argbType = new ARGBType();
		selectionColoringModel.convert( imageSegment, argbType );
		return new Color3f( ColorUtils.getColor( argbType ) );
	}

	private int getLevel( ArrayList< double[] > calibrations, double voxelSpacing3DView )
	{
		int level;

		for ( level = 0; level < calibrations.size(); level++ )
			if ( calibrations.get( level )[ 0 ] > voxelSpacing3DView ) break;

		return level;
	}


	public void close()
	{
		// TODO
	}
}
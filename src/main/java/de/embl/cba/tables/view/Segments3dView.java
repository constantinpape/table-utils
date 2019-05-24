package de.embl.cba.tables.view;

import bdv.viewer.Source;
import customnode.CustomTriangleMesh;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.objects3d.FloodFill;
import de.embl.cba.tables.Logger;
import de.embl.cba.tables.ij3d.AnimatedViewAdjuster;
import de.embl.cba.tables.mesh.MeshExtractor;
import de.embl.cba.tables.mesh.MeshUtils;
import de.embl.cba.tables.color.*;
import de.embl.cba.tables.image.ImageSourcesModel;
import de.embl.cba.tables.imagesegment.ImageSegment;
import de.embl.cba.tables.select.SelectionListener;
import de.embl.cba.tables.select.SelectionModel;
import ij.IJ;
import ij3d.Content;
import ij3d.DefaultUniverse;
import ij3d.Image3DUniverse;
import ij3d.UniverseListener;
import isosurface.MeshEditor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.java3d.View;
import org.scijava.vecmath.Color3f;

import java.awt.*;
import java.awt.Component;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Segments3dView < T extends ImageSegment >
{
	private final List< T > segments;
	private final SelectionModel< T > selectionModel;
	private final SelectionColoringModel< T > selectionColoringModel;
	private final ImageSourcesModel imageSourcesModel;


	private Image3DUniverse universe;
	private T recentFocus;
	private Double voxelSpacing3DView;
	private ConcurrentHashMap< T, CustomTriangleMesh > segmentToMesh;
	private ConcurrentHashMap< T, Content > segmentToContent;
	private ConcurrentHashMap< Content, T > contentToSegment;
	private double transparency;
	private boolean isListeningToUniverse;
	private int meshSmoothingIterations;
	private int segmentFocusAnimationDurationMillis;
	private boolean contentModificationInProgress;
	private double segmentFocusZoomLevel;
	private double segmentFocusDxyMin;
	private double segmentFocusDzMin;
	private long maxNumBoundingBoxElements;
	private Component parentComponent;
	private AtomicBoolean showSegments = new AtomicBoolean( true );
	private ConcurrentHashMap< T, CustomTriangleMesh > segmentToTriangleMesh;
	private ExecutorService executorService;

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

		this.transparency = 0.0;
		this.voxelSpacing3DView = null;
		this.meshSmoothingIterations = 5;
		this.segmentFocusAnimationDurationMillis = 750;
		this.segmentFocusZoomLevel = 0.8;
		this.segmentFocusDxyMin = 20.0;
		this.segmentFocusDzMin = 20.0;
		this.maxNumBoundingBoxElements = 100 * 100 * 100;

		this.executorService = Executors.newFixedThreadPool( 2 * Runtime.getRuntime().availableProcessors() );

		this.segmentToMesh = new ConcurrentHashMap<>();
		this.segmentToContent = new ConcurrentHashMap<>();
		this.contentToSegment = new ConcurrentHashMap<>();

		registerAsSelectionListener( this.selectionModel );
		registerAsColoringListener( this.selectionColoringModel );
	}

	public void setParentComponent( Component parentComponent )
	{
		this.parentComponent = parentComponent;
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

	public void setSegmentFocusZoomLevel( double segmentFocusZoomLevel )
	{
		this.segmentFocusZoomLevel = segmentFocusZoomLevel;
	}

	public void setSegmentFocusDxyMin( double segmentFocusDxyMin )
	{
		this.segmentFocusDxyMin = segmentFocusDxyMin;
	}

	public void setSegmentFocusDzMin( double segmentFocusDzMin )
	{
		this.segmentFocusDzMin = segmentFocusDzMin;
	}

	public void setMaxNumBoundingBoxElements( long maxNumBoundingBoxElements )
	{
		this.maxNumBoundingBoxElements = maxNumBoundingBoxElements;
	}

	public Image3DUniverse getUniverse()
	{
		return universe;
	}

	private ArrayList< double[] > getVoxelSpacings( Source< ? > labelsSource )
	{
		final ArrayList< double[] > voxelSpacings = new ArrayList<>();
		final int numMipmapLevels = labelsSource.getNumMipmapLevels();
		for ( int level = 0; level < numMipmapLevels; ++level )
			voxelSpacings.add( BdvUtils.getCalibration( labelsSource, level ) );

		return voxelSpacings;
	}


	private void registerAsColoringListener( ColoringModel< T > coloringModel )
	{
		coloringModel.listeners().add( () -> adaptSegmentColors() );
	}

	private void adaptSegmentColors()
	{
		for ( T segment : segmentToContent.keySet() )
		{
			executorService.submit( () ->
			{
				final Color3f color3f = getColor3f( segment );
				final Content content = segmentToContent.get( segment );
				content.setColor( color3f );
			});
		}
	}



	public void registerAsSelectionListener( SelectionModel< T > selectionModel )
	{
		selectionModel.listeners().add( new SelectionListener< T >()
		{
			@Override
			public synchronized void selectionChanged()
			{
				if ( ! showSegments.get() ) return;

				contentModificationInProgress = true;
				final Set< T > selected = selectionModel.getSelected();
				showSelectedSegments( selected );
				removeUnselectedSegments( selected );
				contentModificationInProgress = false;
			}

			@Override
			public synchronized void focusEvent( T selection )
			{
				if ( ! showSegments.get() ) return;

				if ( universe == null ) return;
				if ( universe.getContents().size() == 0 ) return;
				if ( selection == recentFocus ) return;
				if ( ! segmentToContent.containsKey( selection ) ) return;

				recentFocus = selection;

				final AnimatedViewAdjuster adjuster =
						new AnimatedViewAdjuster(
								universe,
								AnimatedViewAdjuster.ADJUST_BOTH );

				adjuster.apply(
						segmentToContent.get( selection ),
						30,
						segmentFocusAnimationDurationMillis,
						segmentFocusZoomLevel,
						segmentFocusDxyMin,
						segmentFocusDzMin );
			}
		} );
	}

	private void removeUnselectedSegments( Set< T > selectedSegments )
	{
		final Set< T > currentSegments = segmentToContent.keySet();
		final Set< T > remove = new HashSet<>();

		for ( T segment : currentSegments )
			if ( ! selectedSegments.contains( segment ) )
				remove.add( segment );

		for( T segment : remove )
			removeSegmentFrom3DView( segment );
	}

	private synchronized void showSelectedSegments( Set< T > segments )
	{

//		segmentToTriangleMesh = new ConcurrentHashMap<>();

		initUniverseAndListener();


		final ArrayList< Future > futures = new ArrayList<>();
		for ( T segment : segments )
			if ( ! segmentToContent.containsKey( segment ) )
			{
				futures.add(
						executorService.submit( () ->
								{
//									segmentToTriangleMesh.put( segment, getCustomTriangleMesh( segment ) );
									final CustomTriangleMesh mesh = getCustomTriangleMesh( segment );
									if ( mesh != null )
										addMeshToUniverse( segment, mesh  );
									else
										Logger.info( "Error creating mesh of segment " + segment.labelId() );
								}
						) );
			}

		int i = 1;
		for ( Future future : futures )
		{
			try
			{
				future.get();
				Logger.info( "Added object to 3D Viewer " + (i++) + "/" + futures.size() );
			} catch ( InterruptedException e )
			{
				e.printStackTrace();
			} catch ( ExecutionException e )
			{
				e.printStackTrace();
			}
		}



//		for ( T segment : segments )
//		{
//			executorService.submit( () -> {
//				final CustomTriangleMesh mesh = segmentToTriangleMesh.get( segment );
//				if ( mesh != null )
//				{
//					addMeshToUniverse( segment, mesh );
//					Logger.info( "Added mesh " + ( segment.labelId()) + "/" + segments.size() );
//				}
//				else
//				{
//					Logger.info( "Error creating mesh of segment " + segment.labelId() );
//				}
//			} );
//		}
	}

	private synchronized void removeSegmentFrom3DView( T segment )
	{
		final Content content = segmentToContent.get( segment );
		universe.removeContent( content.getName() );
		segmentToContent.remove( segment );
		contentToSegment.remove( content );
	}

	private CustomTriangleMesh getCustomTriangleMesh( T segment )
	{
		CustomTriangleMesh triangleMesh = getTriangleMesh( segment );
		if ( triangleMesh == null ) return null;
		MeshEditor.smooth2( triangleMesh, meshSmoothingIterations );
		return triangleMesh;
	}

	private CustomTriangleMesh getTriangleMesh( T segment )
	{
		if ( segment.getMesh() == null )
		{
			final float[] mesh = createMesh( segment );
			if ( mesh == null )
				return null;
			else
				segment.setMesh( mesh );
		}

		CustomTriangleMesh triangleMesh = MeshUtils.asCustomTriangleMesh(
				segment.getMesh() );
		triangleMesh.setColor( getColor3f( segment ) );
		return triangleMesh;
	}

	private float[] createMesh( ImageSegment segment )
	{
		final Source< ? > labelsSource =
				imageSourcesModel.sources().get( segment.imageId() ).source();

		int level = getLevel( labelsSource );

		final double[] voxelSpacing = getVoxelSpacings( labelsSource ).get( level );

		final RandomAccessibleInterval< ? extends RealType< ? > > labelsRAI =
				getLabelsRAI( segment, level );

		if ( segment.boundingBox() == null )
			setSegmentBoundingBox( segment, labelsRAI, voxelSpacing );

		FinalInterval boundingBox =
				getIntervalVoxels( segment.boundingBox(), voxelSpacing );

		final long numElements = Intervals.numElements( boundingBox );

		if ( numElements > maxNumBoundingBoxElements )
		{
			Logger.error( "The bounding box of the selected segment has " + numElements + " voxels.\n" +
					"The maximum enabled number is " + maxNumBoundingBoxElements + ".\n" +
					"Thus the image segment will not be displayed in 3D." );
			return null;
		}

//		ImageJFunctions.show( ( RandomAccessibleInterval ) Views.interval( labelsRAI, boundingBox ) );

		final MeshExtractor meshExtractor = new MeshExtractor(
				Views.extendZero( ( RandomAccessibleInterval ) labelsRAI ),
				boundingBox,
				new AffineTransform3D(),
				new int[]{ 1, 1, 1 },
				() -> false );

		final float[] meshCoordinates = meshExtractor.generateMesh( segment.labelId() );

		if ( meshCoordinates.length == 0 )
		{
			Logger.warn( "Could not find any pixels for segment with label " + segment.labelId()
					+ "\nwithin bounding box " + boundingBox );
			return null;
		}

		return meshCoordinates;
	}

	private int getLevel( Source< ? > labelsSource )
	{
		int level;
		if ( voxelSpacing3DView != null )
			level = getLevel( getVoxelSpacings( labelsSource ), voxelSpacing3DView );
		else
			level = 0;
		return level;
	}

	public synchronized void setShowSegments( AtomicBoolean showSegments )
	{
		this.showSegments = showSegments;
	}

	private void setSegmentBoundingBox(
			ImageSegment segment,
			RandomAccessibleInterval< ? extends RealType< ? > > labelsRAI,
			double[] voxelSpacing )
	{
		final long[] voxelCoordinate =
				getSegmentCoordinateVoxels( segment, voxelSpacing );

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

		segment.setBoundingBox( new FinalRealInterval( min, max ) );
	}

	private long[] getSegmentCoordinateVoxels(
			ImageSegment segment,
			double[] calibration )
	{
		final long[] voxelCoordinate = new long[ segment.numDimensions() ];
		for ( int d = 0; d < segment.numDimensions(); d++ )
			voxelCoordinate[ d ] = ( long ) (
					segment.getDoublePosition( d ) / calibration[ d ] );
		return voxelCoordinate;
	}

	private FinalInterval getIntervalVoxels(
			FinalRealInterval realInterval,
			double[] calibration )
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
		final Content content =
				universe.addCustomMesh( mesh, "" + segment.labelId() );

		content.setTransparency( ( float ) transparency );
		content.setLocked( true );

		segmentToContent.put( segment, content );
		contentToSegment.put( content, segment );

		universe.setAutoAdjustView( false );
	}

	private synchronized void initUniverseAndListener()
	{
		if ( universe == null )
			universe = new Image3DUniverse();

		if ( universe.getWindow() == null )
		{
			universe.show();
			universe.getWindow().setResizable( false );

			if ( parentComponent != null )
			{
				universe.getWindow().setPreferredSize( new Dimension(
						parentComponent.getWidth() / 2 ,
						parentComponent.getHeight() / 2  ) );

				universe.getWindow().setLocation(
						parentComponent.getLocationOnScreen().x
								- universe.getWindow().getWidth() - 10,
						parentComponent.getLocationOnScreen().y
				);

			}

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

				// TODO: maybe try to synch this with the Bdv View

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
				if ( c == null ) return;

				if ( ! contentToSegment.containsKey( c ) )
					return;

				final T segment = contentToSegment.get( c );

				if ( selectionModel.isFocused( segment ) )
					return;
				else
				{
					recentFocus = segment; // This avoids "self-focusing"
					selectionModel.focus( segment );
				}

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
			if ( calibrations.get( level )[ 0 ] >= voxelSpacing3DView ) break;

		return level;
	}


	public void close()
	{
		// TODO
	}
}

package de.embl.cba.tables.view;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.*;
import bdv.viewer.Source;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.objects3d.ConnectedComponentExtractorAnd3DViewer;
import de.embl.cba.bdv.utils.overlays.BdvGrayValuesOverlay;
import de.embl.cba.bdv.utils.sources.ARGBConvertedRealSource;
import de.embl.cba.tables.color.*;
import de.embl.cba.tables.image.ImageSourcesModel;
import de.embl.cba.tables.image.SourceMetadata;
import de.embl.cba.tables.image.SourceAndMetadata;
import de.embl.cba.tables.imagesegment.ImageSegment;
import de.embl.cba.tables.imagesegment.LabelFrameAndImage;
import de.embl.cba.tables.imagesegment.SegmentUtils;
import de.embl.cba.tables.select.SelectionListener;
import de.embl.cba.tables.select.SelectionModel;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.util.*;

import static de.embl.cba.bdv.utils.converters.SelectableVolatileARGBConverter.BACKGROUND;
import static de.embl.cba.tables.image.SourceMetadata.*;

// TODO: reconsider what a "segment" needs to be here
public class SegmentsBdvView< T extends ImageSegment >
{
	private String selectTrigger = "ctrl button1";
	private String selectNoneTrigger = "ctrl N";
	private String incrementCategoricalLutRandomSeedTrigger = "ctrl L";
	private String iterateSelectionModeTrigger = "ctrl S";
	private String viewIn3DTrigger = "ctrl shift button1";

	private final SelectionModel< T > selectionModel;
	private final SelectionColoringModel< T > selectionColoringModel;

	private final ImageSourcesModel imageSourcesModel;
	private Behaviours behaviours;

	private BdvHandle bdv;
	private String segmentsName;
	private BdvOptions bdvOptions;
	private SourceAndMetadata labelsSource;
	private LabelsARGBConverter labelsSourceConverter;

	private T recentFocus;
	private ViewerState recentViewerState;
	private List< ConverterSetup > recentConverterSetups;
	private double voxelSpacing3DView;
	private Set< SourceAndMetadata< ? extends RealType< ? > > > currentSources;
	private boolean grayValueOverlayWasFirstSource;
	private HashMap< LabelFrameAndImage, T > labelFrameAndImageToSegment;
	private List< T > segments;
	private int segmentFocusAnimationDurationMillis;

	public SegmentsBdvView(
			final List< T > segments,
			final SelectionModel< T > selectionModel,
			final SelectionColoringModel< T > selectionColoringModel,
			final ImageSourcesModel imageSourcesModel )
	{

		this( segments, selectionModel, selectionColoringModel, imageSourcesModel, null );
	}


	/**
	 *
	 * There can be multiple label images, both in the segments list as well as in
	 * the imageSourcesModel. However, only one of the label images can be displayed at
	 * a time. If a segment is selected in the segments list, the appropriate label
	 * source is identified and shown.
	 *
	 * @param segments
	 * @param selectionModel
	 * @param selectionColoringModel
	 * @param imageSourcesModel
	 * @param bdv
	 */
	public SegmentsBdvView(
			final List< T > segments,
			final SelectionModel< T > selectionModel,
			final SelectionColoringModel< T > selectionColoringModel,
			final ImageSourcesModel imageSourcesModel,
			BdvHandle bdv )
	{
		this.selectionModel = selectionModel;
		this.selectionColoringModel = selectionColoringModel;
		this.imageSourcesModel = imageSourcesModel;
		this.bdv = bdv;

		this.voxelSpacing3DView = 0.1;
		this.segmentFocusAnimationDurationMillis = 750;
		this.currentSources = new HashSet<>( );

		initSegments( segments );
		initBdvOptions();
		showInitialSources();
		addGrayValueOverlay();

		registerAsSelectionListener( this.selectionModel );
		registerAsColoringListener( this.selectionColoringModel );

		installBdvBehaviours();
	}

	public void initSegments( List< T > segments )
	{
		if ( segments != null )
		{
			this.segments = segments;
			this.labelFrameAndImageToSegment = SegmentUtils.createSegmentMap( this.segments );
		}
	}

	public void addGrayValueOverlay()
	{
		// TODO: put this to lower right corner not to interfere with the scale bar
		new BdvGrayValuesOverlay( bdv, 20 ).getBdvOverlaySource();
		grayValueOverlayWasFirstSource = false;
		//hasGrayValueOverlay = true;
	}

	public ImageSourcesModel getImageSourcesModel()
	{
		return imageSourcesModel;
	}

	private void showInitialSources()
	{
		boolean isShownNone = true;

		for ( SourceAndMetadata< ? > sourceAndMetadata : imageSourcesModel.sources().values() )
		{
			if ( sourceAndMetadata.metadata().showInitially )
			{
				showSourceSet( sourceAndMetadata, false );
				isShownNone = false;
			}
		}


		if ( isShownNone )
		{
			showSourceSet(
					// TODO: how get an entry of map values?
					imageSourcesModel.sources().values().iterator().next(),
					false );
		}

	}

	public BdvHandle getBdv()
	{
		return bdv;
	}

	public void registerAsColoringListener( ColoringModel< T > coloringModel )
	{
		coloringModel.listeners().add( () -> BdvUtils.repaint( bdv ) );
	}

	public void registerAsSelectionListener( SelectionModel< T > selectionModel )
	{
		selectionModel.listeners().add( new SelectionListener< T >()
		{
			@Override
			public void selectionChanged()
			{
				BdvUtils.repaint( bdv );
			}

			@Override
			public void focusEvent( T selection )
			{
				if ( recentFocus != null && selection == recentFocus )
				{
					return;
				}
				else
				{
					recentFocus = selection;
					centerBdvOnSegment( selection );
				}
			}
		} );
	}

	public synchronized void centerBdvOnSegment( ImageSegment imageSegment )
	{
		ensureVisibilityOfImageSetOfImageSegment( imageSegment );

		final double[] position = new double[ 3 ];
		imageSegment.localize( position );

		BdvUtils.moveToPosition(
				bdv,
				position,
				imageSegment.timePoint(),
				segmentFocusAnimationDurationMillis );
	}

	public void setSegmentFocusAnimationDurationMillis( int duration )
	{
		this.segmentFocusAnimationDurationMillis = duration;
	}

	private void ensureVisibilityOfImageSetOfImageSegment( ImageSegment imageSegment )
	{
		final String imageId = imageSegment.imageId();

		if ( labelsSource.metadata().imageId.equals( imageId ) )
		{
			// Nothing to be done, the imageSegment belongs to the
			// currently shown image set.
			return;
		}
		else
		{
			// Replace sources, because selected image segment
			// belongs to another image set of displayed data set

			final SourceAndMetadata sourceAndMetadata
					= imageSourcesModel.sources().get( imageId );

			showSourceSet( sourceAndMetadata, true );
		}
	}

	public void setVoxelSpacing3DView( double voxelSpacing3DView )
	{
		this.voxelSpacing3DView = voxelSpacing3DView;
	}

	/**
	 * ...will show more sources if required by metadata...
	 *
	 * @param sourceAndMetadata
	 * @param removeOtherSources
	 */
	public void showSourceSet( SourceAndMetadata sourceAndMetadata, boolean removeOtherSources )
	{
		final List< String > imageSetIDs = sourceAndMetadata.metadata().imageSetIDs;

		if ( bdv != null && removeOtherSources )
			removeSources();

		for ( int associatedSourceIndex = 0;
			  associatedSourceIndex < imageSetIDs.size();
			  associatedSourceIndex++ )
		{
			final SourceAndMetadata associatedSourceAndMetadata =
					imageSourcesModel.sources().get( imageSetIDs.get( associatedSourceIndex ) );

			applyRecentDisplaySettings( associatedSourceIndex, associatedSourceAndMetadata );
			showSource( associatedSourceAndMetadata );
		}

		applyRecentViewerSettings( );
	}

	public void applyRecentViewerSettings( )
	{
		if ( recentViewerState != null )
		{
			applyViewerStateTransform( recentViewerState );

			applyViewerStateVisibility( recentViewerState );
		}
	}

	public void applyViewerStateVisibility( ViewerState recentViewerState )
	{
		final int numSources = bdv.getViewerPanel().getVisibilityAndGrouping().numSources();
		final List< Integer > visibleSourceIndices = recentViewerState.getVisibleSourceIndices();

		for ( int i = 1; i < numSources; i++ )
		{
			int recentSourceIndex = i;

			if ( ! grayValueOverlayWasFirstSource )
				recentSourceIndex--;

			bdv.getViewerPanel().getVisibilityAndGrouping().
					setSourceActive(
							i,
							visibleSourceIndices.contains( recentSourceIndex ) );
		}
	}


	public void applyViewerStateTransform( ViewerState viewerState )
	{
		final AffineTransform3D transform3D = new AffineTransform3D();
		viewerState.getViewerTransform( transform3D );
		bdv.getViewerPanel().setCurrentViewerTransform( transform3D );
	}

	private void applyRecentDisplaySettings( int associatedSourceIndex,
											 SourceAndMetadata associatedSourceAndMetadata )
	{
		if ( recentConverterSetups != null )
		{
			int recentConverterSetupIndex = associatedSourceIndex;

			if ( grayValueOverlayWasFirstSource )
				recentConverterSetupIndex++;

			associatedSourceAndMetadata.metadata().displayRangeMin =
					recentConverterSetups.get( recentConverterSetupIndex ).getDisplayRangeMin();

			associatedSourceAndMetadata.metadata().displayRangeMax =
					recentConverterSetups.get( recentConverterSetupIndex ).getDisplayRangeMax();
		}
	}

	public BdvStackSource showSource( SourceAndMetadata sourceAndMetadata )
	{
		final SourceMetadata metadata = sourceAndMetadata.metadata();
		Source< ? > source = sourceAndMetadata.source();

		if ( metadata.flavour == Flavour.LabelSource )
			source = asLabelsSource( sourceAndMetadata );

		bdvOptions = bdvOptions.sourceTransform( metadata.sourceTransform );

		int numTimePoints = getNumTimePoints( source );

		final BdvStackSource bdvStackSource = BdvFunctions.show(
				source,
				numTimePoints,
				bdvOptions );

		bdvStackSource.setActive( true );

		bdvStackSource.setDisplayRange( metadata.displayRangeMin, metadata.displayRangeMax );

		bdv = bdvStackSource.getBdvHandle();

		bdvOptions = bdvOptions.addTo( bdv );

		metadata.bdvStackSource = bdvStackSource;

		currentSources.add( sourceAndMetadata );

		if ( labelsSourceConverter != null )
			bdv.getViewerPanel().addTimePointListener( labelsSourceConverter );

		return bdvStackSource;
	}

	public int getNumTimePoints( Source< ? > source )
	{
		int numTimePoints = 0;
		while ( source.isPresent( numTimePoints++ ) ){}
		return numTimePoints - 1;
	}

	public void removeSource( BdvStackSource bdvStackSource )
	{
		currentSources.remove( bdvStackSource );
		BdvUtils.removeSource( bdv, bdvStackSource );
	}

	public ArrayList< SourceAndMetadata< ? extends RealType< ? > > > getCurrentSources()
	{
		return new ArrayList<>( currentSources );
	}

	private void removeSources()
	{
		recentViewerState = bdv.getViewerPanel().getState();
		recentConverterSetups = new ArrayList<>( bdv.getSetupAssignments().getConverterSetups() );

		final List< SourceState< ? > > sources = recentViewerState.getSources();
		final int numSources = sources.size();

		for ( int i = 0; i < numSources; ++i )
		{
			final Source< ? > source = sources.get( i ).getSpimSource();
			if ( source instanceof PlaceHolderSource )
			{
				if ( i == 0 )
					grayValueOverlayWasFirstSource = true;
			}
			else
			{
				bdv.getViewerPanel().removeSource( source );
				bdv.getSetupAssignments().removeSetup( recentConverterSetups.get( i ) );
			}
		}
	}


	/**
	 * Currently, the logic is that there can be only one labels source
	 * within this SegmentsBdvView class. Thus, creation of the labels source
	 * also registers it as "the" labelsSource
	 *
	 * @param sourceAndMetadata
	 * @return
	 */
	private Source asLabelsSource(
			SourceAndMetadata< ? extends RealType< ? > > sourceAndMetadata )
	{

		this.labelsSource = sourceAndMetadata;

		final LabelsARGBConverter labelsARGBConverter =
				createLabelsARGBConverter( labelsSource );

		this.labelsSourceConverter = labelsARGBConverter;

		final ARGBConvertedRealSource convertedRealSource =
				new ARGBConvertedRealSource( sourceAndMetadata.source(), labelsARGBConverter );

		return convertedRealSource;
	}

	private LabelsARGBConverter createLabelsARGBConverter( SourceAndMetadata labelsSource )
	{
		LabelsARGBConverter labelsARGBConverter;

		if ( labelFrameAndImageToSegment == null )
		{
			labelsARGBConverter = new LazyLabelsARGBConverter();
		}
		else
		{
			labelsARGBConverter =
					new SegmentsARGBConverter(
							labelFrameAndImageToSegment,
							labelsSource.metadata().imageId,
							selectionColoringModel );

		}
		return labelsARGBConverter;
	}

	private void initBdvOptions( )
	{
		bdvOptions = BdvOptions.options();

		if ( imageSourcesModel.is2D() )
			bdvOptions = bdvOptions.is2D();

		if ( bdv != null )
			bdvOptions = bdvOptions.addTo( bdv );
	}

	private void installBdvBehaviours()
	{
		segmentsName = segments.toString();

		behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install(
				bdv.getBdvHandle().getTriggerbindings(),
				segmentsName + "-bdv-select-handler" );


		installSelectionBehaviour( );
		installSelectNoneBehaviour( );
		installSelectionColoringModeBehaviour( );
		installRandomColorShufflingBehaviour();
		install3DViewBehaviour();
	}

	private void installRandomColorShufflingBehaviour()
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
						new Thread( () -> shuffleRandomColors() ).start(),
				segmentsName + "-change-color-random-seed",
				incrementCategoricalLutRandomSeedTrigger );
	}

	private synchronized void shuffleRandomColors()
	{
		if ( ! isLabelSourceActive() ) return;

		final ColoringModel< T > coloringModel =
				selectionColoringModel.getWrappedColoringModel();

		if ( coloringModel instanceof CategoryColoringModel )
		{
			( ( CategoryColoringModel ) coloringModel ).incRandomSeed();
			BdvUtils.repaint( bdv );
		}
	}


	private void installSelectNoneBehaviour( )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
				new Thread( () -> selectNone() ).start(),
				segmentsName + "-select-none", selectNoneTrigger );
	}

	public synchronized void selectNone()
	{
		if ( ! isLabelSourceActive() ) return;

		selectionModel.clearSelection( );

		BdvUtils.repaint( bdv );
	}

	private void installSelectionBehaviour()
	{
		behaviours.behaviour(
				( ClickBehaviour ) ( x, y ) ->
						new Thread( () -> toggleSelectionAtMousePosition() ).start(),
				segmentsName + "-toggle-select", selectTrigger ) ;
	}

	private synchronized void toggleSelectionAtMousePosition()
	{
		if ( segments == null )
		{
			// TODO: maybe also make this work if there are now segments, i.e. a table provided
			return;
		}

		if ( ! isLabelSourceActive() ) return;

		final double labelId = getLabelIdAtCurrentMouseCoordinates( labelsSource );

		if ( labelId == BACKGROUND ) return;

		final String labelImageId = labelsSource.metadata().imageId;

		final LabelFrameAndImage labelFrameAndImage =
				new LabelFrameAndImage( labelId, getCurrentTimePoint(),labelImageId );

		final T segment = labelFrameAndImageToSegment.get( labelFrameAndImage );

		selectionModel.toggle( segment );

		if ( selectionModel.isSelected( segment ) )
		{
			recentFocus = segment;
			selectionModel.focus( segment );
		}
	}

	private boolean isLabelSourceActive()
	{
		final Source< ? > source
				= labelsSource.metadata().bdvStackSource
				.getSources().get( 0 ).getSpimSource();

		final boolean active = BdvUtils.isActive( bdv, source );

		return active;
	}

	private double getLabelIdAtCurrentMouseCoordinates( SourceAndMetadata activeLabelSource )
	{
		final RealPoint globalMouseCoordinates =
				BdvUtils.getGlobalMouseCoordinates( bdv );

		final Double value = BdvUtils.getValueAtGlobalCoordinates(
				activeLabelSource.source(),
				globalMouseCoordinates,
				getCurrentTimePoint() );

		if ( value == null )
			System.err.println(
					"Could not find pixel value at position " + globalMouseCoordinates);

		return value;
	}

	private void installSelectionColoringModeBehaviour( )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
				new Thread( () ->
				{
					if ( ! isLabelSourceActive() ) return;
					selectionColoringModel.iterateSelectionMode();
					BdvUtils.repaint( bdv );
				} ).start(),
				segmentsName + "-iterate-select", iterateSelectionModeTrigger );
	}

	private void install3DViewBehaviour()
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->

				new Thread( () -> {

					if ( labelsSource == null ) return;
					if ( ! isLabelSourceActive() ) return;
					if ( getLabelIdAtCurrentMouseCoordinates( labelsSource ) == BACKGROUND ) return;

					viewObjectAtCurrentMouseCoordinatesIn3D( labelsSource );

				}).start(),
				segmentsName + "-view-3d", viewIn3DTrigger );
	}

	private synchronized void viewObjectAtCurrentMouseCoordinatesIn3D(
			SourceAndMetadata activeLabelSource )
	{
		new ConnectedComponentExtractorAnd3DViewer( activeLabelSource.source() )
				.extractAndShowIn3D(
						BdvUtils.getGlobalMouseCoordinates( bdv ),
						voxelSpacing3DView );
	}

	private int getCurrentTimePoint()
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getCurrentTimepoint();
	}


	public void close()
	{
		// TODO
	}
}
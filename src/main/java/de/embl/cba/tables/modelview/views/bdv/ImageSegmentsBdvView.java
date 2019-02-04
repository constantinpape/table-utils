package de.embl.cba.tables.modelview.views.bdv;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.Source;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.overlays.BdvGrayValuesOverlay;
import de.embl.cba.bdv.utils.sources.ARGBConvertedRealSource;
import de.embl.cba.tables.modelview.coloring.ColoringListener;
import de.embl.cba.tables.modelview.coloring.ColoringModel;
import de.embl.cba.tables.modelview.coloring.DynamicCategoryColoringModel;
import de.embl.cba.tables.modelview.coloring.SelectionColoringModel;
import de.embl.cba.tables.modelview.combined.ImageSegmentsModel;
import de.embl.cba.tables.modelview.images.ImageSourcesModel;
import de.embl.cba.tables.modelview.images.Metadata;
import de.embl.cba.tables.modelview.images.SourceAndMetadata;
import de.embl.cba.tables.modelview.segments.ImageSegment;
import de.embl.cba.tables.modelview.segments.ImageSegmentId;
import de.embl.cba.tables.modelview.selection.SelectionListener;
import de.embl.cba.tables.modelview.selection.SelectionModel;
import de.embl.cba.tables.modelview.views.ImageSegmentLabelsARGBConverter;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.util.*;

import static de.embl.cba.bdv.utils.converters.SelectableVolatileARGBConverter.BACKGROUND;
import static de.embl.cba.tables.modelview.images.Metadata.*;

public class ImageSegmentsBdvView < T extends ImageSegment >
{
	private String selectTrigger = "ctrl button1";
	private String selectNoneTrigger = "ctrl N";
	private String incrementCategoricalLutRandomSeedTrigger = "ctrl L";
	private String iterateSelectionModeTrigger = "ctrl S";
	private String viewIn3DTrigger = "ctrl shift button1";

	private final ImageSegmentsModel< T > imageSegmentsModel;
	private final ImageSourcesModel imageSourcesModel;
	private final SelectionModel< T > selectionModel;
	private final SelectionColoringModel< T > selectionColoringModel;
	private Behaviours behaviours;

	private BdvHandle bdv;
	private String name = "TODO";
	private BdvOptions bdvOptions;
	private SourceAndMetadata currentLabelSource;
	private T recentFocus;
	private ViewerState recentViewerState;
	private List< ConverterSetup > recentConverterSetups;

	public ImageSegmentsBdvView(
			final ImageSourcesModel imageSourcesModel,
			final ImageSegmentsModel< T > imageSegmentsModel,
			final SelectionModel< T > selectionModel,
			final SelectionColoringModel< T > selectionColoringModel)
	{
		this.imageSourcesModel = imageSourcesModel;
		this.imageSegmentsModel = imageSegmentsModel;
		this.selectionModel = selectionModel;
		this.selectionColoringModel = selectionColoringModel;

		initBdvOptions( );

		showSource( this.imageSourcesModel.sources().values().iterator().next() );

		new BdvGrayValuesOverlay( bdv, 20 );

		registerAsSelectionListener( selectionModel );

		registerAsColoringListener( selectionColoringModel );

		installBdvBehaviours();
	}

	public BdvHandle getBdv()
	{
		return bdv;
	}

	public void registerAsColoringListener( ColoringModel< T > coloringModel )
	{
		coloringModel.listeners().add( new ColoringListener()
		{
			@Override
			public void coloringChanged()
			{
				BdvUtils.repaint( bdv );
			}
		} );
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
				if ( recentFocus != null
						&& selection == recentFocus )
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
		showSegmentImage( imageSegment );

		bdv.getBdvHandle().getViewerPanel().setTimepoint( imageSegment.timePoint() );

		final double[] position = new double[ 3 ];
		imageSegment.localize( position );
		BdvUtils.moveToPosition(
				bdv,
				position,
				imageSegment.timePoint(),
				500 );
	}

	private void showSegmentImage( ImageSegment imageSegment )
	{
		final String imageId = imageSegment.imageId();

		if ( currentLabelSource.metadata().getMap().get( NAME ).equals( imageId ) ) return;

		final SourceAndMetadata sourceAndMetadata
				= imageSourcesModel.sources().get( imageId );

		showSource( sourceAndMetadata );

	}

	/**
	 * ...will show more sources if required by metadata...
	 *
	 * @param sourceAndMetadata
	 */
	public void showSource( SourceAndMetadata sourceAndMetadata )
	{
		final Map< String, Object > metadata = sourceAndMetadata.metadata().getMap();

		if( metadata.containsKey( Metadata.EXCLUSIVE_IMAGE_SET ) )
		{
			showExclusiveImageSet( metadata );
		}
		else
		{
			showSingleSource(
					sourceAndMetadata,
					null,
					null );
		}
	}

	public void applyRecentViewerSettings( )
	{
		if ( recentViewerState != null )
		{
			applyViewerStateTransform( recentViewerState );

			applyViewerStateVisibility( recentViewerState );

			//applyDisplaySettings( recentConverterSetups );

		}
	}

	public void applyViewerStateVisibility( ViewerState viewerState )
	{
		final int numSources = bdv.getViewerPanel().getVisibilityAndGrouping().numSources();
		for ( int i = 0; i < numSources; i++ )
		{
			if ( viewerState.getVisibleSourceIndices().contains( i ) )
			{
				bdv.getViewerPanel().getVisibilityAndGrouping().setSourceActive( i, true );
			}
			else
			{
				bdv.getViewerPanel().getVisibilityAndGrouping().setSourceActive( i, false );
			}
		}
	}


	public void applyViewerStateTransform( ViewerState viewerState )
	{
		final AffineTransform3D transform3D = new AffineTransform3D();
		viewerState.getViewerTransform( transform3D );
		bdv.getViewerPanel().setCurrentViewerTransform( transform3D );
	}

	public void showExclusiveImageSet( Map< String, Object > metadata )
	{
		if ( bdv != null  ) removeAllSources();

		final ArrayList< String > imageIDs = ( ArrayList< String > ) metadata.get( Metadata.EXCLUSIVE_IMAGE_SET );

		for ( int i = 0; i < imageIDs.size(); i++ )
		{
			final SourceAndMetadata associatedSourceAndMetadata =
					imageSourcesModel.sources().get( imageIDs.get( i ) );

			if ( recentConverterSetups != null )
			{
				showSingleSource(
						associatedSourceAndMetadata,
						recentConverterSetups.get( i ).getDisplayRangeMin(),
						recentConverterSetups.get( i ).getDisplayRangeMax() );
			}
			else
			{
				showSingleSource(
						associatedSourceAndMetadata,
						null,
						null );
			}
		}

		applyRecentViewerSettings( );
	}


	/**
	 * Shows a single source
	 *
	 * @param sourceAndMetadata
	 * @param displayRangeMin
	 * @param displayRangeMax
	 */
	public void showSingleSource(
			SourceAndMetadata sourceAndMetadata,
			Double displayRangeMin,
			Double displayRangeMax )
	{
		final Map< String, Object > metadata = sourceAndMetadata.metadata().getMap();
		Source< ? > source = sourceAndMetadata.source();

		if ( metadata.containsKey( FLAVOUR ) && metadata.get( FLAVOUR ).equals( Flavour.LabelSource ) )
		{
			source = asLabelSource( sourceAndMetadata );
			currentLabelSource = sourceAndMetadata;
		}

		if ( metadata.containsKey( NUM_SPATIAL_DIMENSIONS ) && metadata.get( NUM_SPATIAL_DIMENSIONS ).equals( 2 ) )
		{
			bdvOptions = bdvOptions.is2D();
		}

		final BdvStackSource stackSource = BdvFunctions.show( source, bdvOptions );

		if ( displayRangeMin != null && displayRangeMax != null )
		{
			stackSource.setDisplayRange( displayRangeMin, displayRangeMax );
		}

		bdv = stackSource.getBdvHandle();

		bdvOptions = bdvOptions.addTo( bdv );
	}


	private void removeAllSources()
	{

		recentViewerState = bdv.getViewerPanel().getState();
		recentConverterSetups = new ArrayList<>( bdv.getSetupAssignments().getConverterSetups() );

		final List< SourceState< ? > > sources = recentViewerState.getSources();
		final int numSources = sources.size();

		for ( int i = numSources - 1; i >= 0; --i )
		{
			final Source< ? > source = sources.get( i ).getSpimSource();
			bdv.getViewerPanel().removeSource( source );

			final ConverterSetup converterSetup = recentConverterSetups.get( i );
			bdv.getSetupAssignments().removeSetup( converterSetup );
		}
	}

	private Source asLabelSource( SourceAndMetadata sourceAndMetadata )
	{
		ImageSegmentLabelsARGBConverter labelSourcesARGBConverter =
				new ImageSegmentLabelsARGBConverter(
						imageSegmentsModel,
						( String )sourceAndMetadata.metadata().getMap().get( Metadata.NAME ),
						selectionColoringModel );

		return new ARGBConvertedRealSource(
				sourceAndMetadata.source(),
				labelSourcesARGBConverter );
	}

	private void initBdvOptions( )
	{
		bdvOptions = BdvOptions.options();
	}

	private void installBdvBehaviours()
	{
		behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install(
				bdv.getBdvHandle().getTriggerbindings(),
				name + "-bdv-selection-handler" );

		installSelectionBehaviour( );
		installSelectNoneBehaviour( );
		installSelectionColoringModeBehaviour( );
		installRandomColorShufflingBehaviour();
		//if( is3D() ) install3DViewBehaviour();
	}

	private void installRandomColorShufflingBehaviour()
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			if ( selectionColoringModel.getWrappedColoringModel() instanceof DynamicCategoryColoringModel )
			{
				( ( DynamicCategoryColoringModel ) selectionColoringModel.getWrappedColoringModel() ).incRandomSeed();
				BdvUtils.repaint( bdv );
			}
		}, name + "-change-coloring-random-seed", incrementCategoricalLutRandomSeedTrigger );
	}


	private void installSelectNoneBehaviour( )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			selectNone();

		}, name + "-select-none", selectNoneTrigger );
	}

	public void selectNone()
	{
		selectionModel.clearSelection( );

		BdvUtils.repaint( bdv );
	}

	private void installSelectionBehaviour()
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			toggleSelectionAtMousePosition();
		}, name + "-toggle-selection", selectTrigger ) ;
	}

	private void toggleSelectionAtMousePosition()
	{
		if (currentLabelSource == null) {
			return;
		}

		final int timePoint = getCurrentTimePoint();

		final RealPoint imageSegmentCoordinate = BdvUtils.getGlobalMouseCoordinates( bdv );

		final double labelId = BdvUtils.getValueAtGlobalCoordinates(
				currentLabelSource.source(),
				imageSegmentCoordinate,
				timePoint );

		if ( labelId == BACKGROUND ) return;

		if ( currentLabelSource.metadata().getMap().containsKey( NAME ) )
		{
			final String imageId = ( String ) currentLabelSource.metadata().getMap().get( NAME );

			final ImageSegmentId imageSegmentId = new ImageSegmentId( imageId, labelId, timePoint );

			final T segment = imageSegmentsModel.getImageSegment( imageSegmentId );

			selectionModel.toggle( segment );

			if ( selectionModel.isSelected( segment ) )
			{
				recentFocus = segment;
				selectionModel.focus( segment );
			}
		}
	}

	private void installSelectionColoringModeBehaviour( )
	{
		behaviours.behaviour( ( ClickBehaviour ) ( x, y ) ->
		{
			selectionColoringModel.iterateSelectionMode();
			BdvUtils.repaint( bdv );
		}, name + "-iterate-selection", iterateSelectionModeTrigger );
	}

	private int getCurrentTimePoint()
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getCurrentTimepoint();
	}

}

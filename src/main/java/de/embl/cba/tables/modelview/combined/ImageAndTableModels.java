package de.embl.cba.tables.modelview.combined;

import de.embl.cba.bdv.utils.lut.GlasbeyARGBLut;
import de.embl.cba.tables.modelview.coloring.DynamicCategoryColoringModel;
import de.embl.cba.tables.modelview.coloring.SelectionColoringModel;
import de.embl.cba.tables.modelview.images.ImageSourcesModel;
import de.embl.cba.tables.modelview.segments.AnnotatedImageSegment;
import de.embl.cba.tables.modelview.segments.DefaultAnnotatedImageSegment;
import de.embl.cba.tables.modelview.selection.DefaultSelectionModel;
import de.embl.cba.tables.modelview.selection.SelectionModel;
import de.embl.cba.tables.modelview.views.bdv.ImageSegmentsBdvView;
import de.embl.cba.tables.modelview.views.table.TableRowsTableView;

import java.util.ArrayList;

public class ImageAndTableModels
{
	/**
	 * Builds derived models and views from the inputs.
	 *  @param imageSourcesModel
	 * @param annotatedImageSegments
	 * @param categoricalColumns
	 * @param initialSources
	 */
	public static void buildModelsAndViews(
			ImageSourcesModel imageSourcesModel,
			ArrayList< DefaultAnnotatedImageSegment > annotatedImageSegments,
			ArrayList< String > categoricalColumns )
	{
//		final AnnotatedImageSegmentsModel imageSegmentsModel =
//				new AnnotatedImageSegmentsModel(
//						"Data",
//						annotatedImageSegments,
//						imageSourcesModel );
//
//		new DefaultImageSegmentsModel< AnnotatedImageSegment >( annotatedImageSegments )
//
//		final SelectionModel< AnnotatedImageSegment > selectionModel
//				= new DefaultSelectionModel<>();
//
//		final DynamicCategoryColoringModel< AnnotatedImageSegment > coloringModel
//				= new DynamicCategoryColoringModel<>( new GlasbeyARGBLut(), 50 );
//
//		final SelectionColoringModel< AnnotatedImageSegment > selectionColoringModel
//				= new SelectionColoringModel<>(
//				coloringModel,
//				selectionModel );
//
//		final ImageSegmentsBdvView imageSegmentsBdvView =
//				new ImageSegmentsBdvView(
//						imageSourcesModel,
//						imageSegmentsModel,
//						selectionModel,
//						selectionColoringModel );
//
//
//		final TableRowsTableView tableView = new TableRowsTableView(
//				imageSegmentsModel,
//				selectionModel,
//				selectionColoringModel,
//				categoricalColumns );
	}


}

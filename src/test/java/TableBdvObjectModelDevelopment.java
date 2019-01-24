import de.embl.cba.bdv.utils.sources.SelectableARGBConvertedRealSource;
import de.embl.cba.tables.TableUtils;
import de.embl.cba.tables.modelview.datamodels.LabelImageSource;
import de.embl.cba.tables.modelview.datamodels.SegmentModel;
import de.embl.cba.tables.modelview.objects.Segment;
import de.embl.cba.tables.modelview.selection.DefaultSelectionModel;
import de.embl.cba.tables.modelview.selection.SelectionModel;
import de.embl.cba.tables.modelview.views.SegmentModelBdvView;
import de.embl.cba.tables.modelview.views.SegmentModelTableView;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public class TableBdvObjectModelDevelopment
{
	public static void main( String[] args ) throws IOException
	{

		final SelectableARGBConvertedRealSource source = Examples.loadSelectableSource();
		final LabelImageSource labelImageSource = new LabelImageSource( source, true );

		final File tableFile = new File( Examples.class.getResource( "2d-16bit-labelMask-Morphometry.csv" ).getFile() );

		final SegmentModel model = new SegmentModel(
				"MyModel",
				tableFile,
				"Label",
				null,
				labelImageSource );


		final SelectionModel< Segment > selectionModel = new DefaultSelectionModel<>();

		final SegmentModelBdvView< Segment > segmentModelBdvView = new SegmentModelBdvView( model, selectionModel );
		final SegmentModelTableView< Segment > segmentModelTableView = new SegmentModelTableView<>( model, selectionModel );

		selectionModel.listeners().add( segmentModelBdvView );
		selectionModel.listeners().add( segmentModelTableView );

	}
}

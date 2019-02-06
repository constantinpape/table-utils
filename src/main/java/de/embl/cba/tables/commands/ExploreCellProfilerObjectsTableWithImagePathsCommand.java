package de.embl.cba.tables.commands;

import de.embl.cba.bdv.utils.lut.GlasbeyARGBLut;
import de.embl.cba.tables.TableUtils;
import de.embl.cba.tables.cellprofiler.FolderAndFileColumn;
import de.embl.cba.tables.modelview.coloring.DynamicCategoryColoringModel;
import de.embl.cba.tables.modelview.coloring.SelectionColoringModel;
import de.embl.cba.tables.modelview.combined.DefaultImageSegmentsModel;
import de.embl.cba.tables.modelview.combined.DefaultTableRowsModel;
import de.embl.cba.tables.modelview.images.FileImageSourcesModel;
import de.embl.cba.tables.modelview.images.ImageSourcesModelFactory;
import de.embl.cba.tables.modelview.images.TableImageSourcesModelFactory;
import de.embl.cba.tables.modelview.segments.*;
import de.embl.cba.tables.modelview.selection.DefaultSelectionModel;
import de.embl.cba.tables.modelview.selection.SelectionModel;
import de.embl.cba.tables.modelview.views.bdv.ImageSegmentsBdvView;
import de.embl.cba.tables.modelview.views.table.TableRowsTableView;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;


@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>Explore>CellProfiler Objects Table" )
public class ExploreCellProfilerObjectsTableWithImagePathsCommand< R extends RealType< R > & NativeType< R > >
		implements Command
{
	public static final String CELLPROFILER_FOLDER_COLUMN_PREFIX = "PathName_";
	public static final String CELLPROFILER_FILE_COLUMN_PREFIX = "FileName_";
	public static final String OBJECTS = "Objects_";
	public static final String COLUMN_NAME_OBJECT_LABEL = "Number_Object_Number";
	public static final String COLUMN_NAME_OBJECT_LOCATION_CENTER_X = "Location_Center_X";
	public static final String COLUMN_NAME_OBJECT_LOCATION_CENTER_Y = "Location_Center_Y";

	@Parameter ( label = "CellProfiler Table" )
	public File inputTableFile;

	@Parameter ( label = "LabelId Image File Name Column" )
	public String labelImageFileNameColumn = "FileName_Objects_Nuclei_Labels";

	@Parameter ( label = "LabelId Image Folder Name Column" )
	public String labelImagePathNameColumn = "PathName_Objects_Nuclei_Labels";

	@Parameter ( label = "Apply Path Mapping" )
	public boolean isPathMapping = false;

	@Parameter ( label = "Image Path Mapping (Table)" )
	public String imageRootPathInTable = "/Volumes/cba/exchange/Daja-Christian/20190116_for_classification_interphase_versus_mitotic";

	@Parameter ( label = "Image Path Mapping (This Computer)" )

	public String imageRootPathOnThisComputer = "/Users/tischer/Documents/daja-schichler-nucleoli-segmentation--data/2019-01-31";
	private HashMap< String, FolderAndFileColumn > imageNameToFolderAndFileColumns;
	private LinkedHashMap< String, ArrayList< Object > > columns;

	@Override
	public void run()
	{

		final ArrayList< ColumnBasedTableRowImageSegment > tableRowImageSegments
				= createAnnotatedImageSegments( inputTableFile );

		final FileImageSourcesModel imageSourcesModel =
				new ImageSourcesModelFactory(
						tableRowImageSegments,
						inputTableFile.toString(),
						2 ).getImageSourcesModel();

		final ArrayList< String > categoricalColumns = new ArrayList<>();
		categoricalColumns.add( "Label" );

		final SelectionModel< ColumnBasedTableRowImageSegment > selectionModel
				= new DefaultSelectionModel<>();

		final DynamicCategoryColoringModel< ColumnBasedTableRowImageSegment > coloringModel
				= new DynamicCategoryColoringModel<>( new GlasbeyARGBLut(), 50 );

		final SelectionColoringModel< ColumnBasedTableRowImageSegment > selectionColoringModel
				= new SelectionColoringModel<>(
					coloringModel,
					selectionModel );

		final DefaultImageSegmentsModel< ColumnBasedTableRowImageSegment > imageSegmentsModel
				= new DefaultImageSegmentsModel<>( tableRowImageSegments );

		final DefaultTableRowsModel< ColumnBasedTableRowImageSegment > tableRowsModel
				= new DefaultTableRowsModel<>( tableRowImageSegments );

		final ImageSegmentsBdvView imageSegmentsBdvView =
				new ImageSegmentsBdvView(
						imageSourcesModel,
						imageSegmentsModel,
						selectionModel,
						selectionColoringModel );

		final TableRowsTableView tableView = new TableRowsTableView(
				tableRowsModel,
				selectionModel,
				selectionColoringModel,
				categoricalColumns );

	}

	public FileImageSourcesModel createCellProfilerImageSourcesModel()
	{
		if ( !isPathMapping )
		{
			imageRootPathInTable = "";
			imageRootPathOnThisComputer = "";
		}

		final TableImageSourcesModelFactory modelCreator =
				new TableImageSourcesModelFactory(
					inputTableFile,
					imageRootPathInTable,
					imageRootPathOnThisComputer,
					"\t",
						2 );

		return modelCreator.getImageSourcesModel();
	}

	private ArrayList< ColumnBasedTableRowImageSegment > createAnnotatedImageSegments( File tableFile )
	{
		columns = TableUtils.columnsFromTableFile( tableFile, null );

		final ArrayList< String > pathColumnNames = replaceFolderAndFileColumnsByPathColumn();

		final HashMap< ImageSegmentCoordinate, ArrayList< Object > > imageSegmentCoordinateToColumn
				= getImageSegmentCoordinateToColumn( pathColumnNames );

		final ArrayList< ColumnBasedTableRowImageSegment > segments
				= SegmentUtils.tableRowImageSegmentsFromColumns( columns, imageSegmentCoordinateToColumn );

		return segments;
	}

	private HashMap< ImageSegmentCoordinate, ArrayList< Object > > getImageSegmentCoordinateToColumn( ArrayList< String > pathColumnNames )
	{
		final HashMap< ImageSegmentCoordinate, ArrayList< Object > > imageSegmentCoordinateToColumn
				= new HashMap<>();

		String labelImagePathColumnName = getLabelImagePathColumnName( pathColumnNames );

		imageSegmentCoordinateToColumn.put(
				ImageSegmentCoordinate.ImageId,
				columns.get( labelImagePathColumnName ));

		imageSegmentCoordinateToColumn.put(
				ImageSegmentCoordinate.LabelId,
				columns.get( COLUMN_NAME_OBJECT_LABEL ) );

		imageSegmentCoordinateToColumn.put(
				ImageSegmentCoordinate.X,
				columns.get( COLUMN_NAME_OBJECT_LOCATION_CENTER_X ) );

		imageSegmentCoordinateToColumn.put(
				ImageSegmentCoordinate.Y,
				columns.get( COLUMN_NAME_OBJECT_LOCATION_CENTER_Y ) );

		return imageSegmentCoordinateToColumn;
	}

	private String getLabelImagePathColumnName( ArrayList< String > pathColumnNames )
	{
		String labelImagePathColumnName = "";
		for ( String pathColumnName : pathColumnNames )
		{
			if ( pathColumnName.contains( OBJECTS ) )
			{
				labelImagePathColumnName = pathColumnName;
				break;
			}
		}
		return labelImagePathColumnName;
	}

	private ArrayList< String > replaceFolderAndFileColumnsByPathColumn()
	{
		final int numRows = columns.values().iterator().next().size();
		imageNameToFolderAndFileColumns = fetchFolderAndFileColumns( columns.keySet() );

		final String tableFile = inputTableFile.toString();

		final ArrayList< String > pathColumnNames = new ArrayList<>();

		for ( String imageName : imageNameToFolderAndFileColumns.keySet() )
		{
			final String fileColumnName = imageNameToFolderAndFileColumns.get( imageName ).fileColumn();
			final String folderColumnName = imageNameToFolderAndFileColumns.get( imageName ).folderColumn();
			final ArrayList< Object > fileColumn = columns.get( fileColumnName );
			final ArrayList< Object > folderColumn = columns.get( folderColumnName );

			final ArrayList< Object > pathColumn = new ArrayList<>();

			for ( int row = 0; row < numRows; row++ )
			{
				String imagePath = folderColumn.get( row ) + File.separator + fileColumn.get( row );

				if ( isPathMapping )
				{
					imagePath = getMappedPath( imagePath );
				}

				imagePath = TableUtils.getRelativePath( tableFile, imagePath ).toString();

				pathColumn.add( imagePath );
			}

			columns.remove( fileColumnName );
			columns.remove( folderColumnName );

			final String pathColumnName = getPathColumnName( imageName );
			columns.put( pathColumnName, pathColumn );
			pathColumnNames.add( pathColumnName );
		}

		return pathColumnNames;
	}

	public String getPathColumnName( String imageName )
	{
		String pathColumn = "Path_" + imageName;

		return pathColumn;
	}


	private String getMappedPath( String imagePath )
	{
		imagePath = imagePath.replace( imageRootPathInTable, imageRootPathOnThisComputer );
		return imagePath;
	}

	public HashMap< String, FolderAndFileColumn > fetchFolderAndFileColumns( Set< String > columns )
	{
		final HashMap< String, FolderAndFileColumn > imageNameToFolderAndFileColumns = new HashMap<>();

		for ( String column : columns )
		{
			if ( column.contains( CELLPROFILER_FOLDER_COLUMN_PREFIX ) )
			{
				final String image = column.split( CELLPROFILER_FOLDER_COLUMN_PREFIX )[ 1 ];
				String fileColumn = getMatchingFileColumn( image, columns );
				imageNameToFolderAndFileColumns.put( image, new FolderAndFileColumn( column, fileColumn ) );
			}
		}
		return imageNameToFolderAndFileColumns;
	}

	private String getMatchingFileColumn( String image, Set< String > columns )
	{
		for ( String column : columns )
		{
			if ( column.contains( CELLPROFILER_FILE_COLUMN_PREFIX ) && column.contains( image ) )
			{
				return column;
			}
		}

		return null;
	}
}
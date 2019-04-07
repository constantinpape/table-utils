package de.embl.cba.tables.ui;

import de.embl.cba.bdv.utils.lut.BlueWhiteRedARGBLut;
import de.embl.cba.bdv.utils.lut.GlasbeyARGBLut;
import de.embl.cba.tables.TableUtils;
import de.embl.cba.tables.modelview.coloring.CategoryTableRowColumnColoringModel;
import de.embl.cba.tables.modelview.coloring.NumericColoringModelDialog;
import de.embl.cba.tables.modelview.coloring.NumericTableRowColumnColoringModel;
import de.embl.cba.tables.modelview.coloring.SelectionColoringModel;
import de.embl.cba.tables.modelview.segments.TableRow;
import ij.gui.GenericDialog;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class ColorByColumnDialog< T extends TableRow >
{

	public static final String LINEAR_BLUE_WHITE_RED = "Linear - Blue White Red";
	public static final String RANDOM_GLASBEY = "Random - Glasbey";
	private String selectedColumnName;
	private String selectedColoringMode;

	private Map< String, double[] > columnNameToMinMax;
	private HashMap< String, double[] > columnNameToRangeSettings;


	public ColorByColumnDialog( )
	{
		this.columnNameToMinMax = new HashMap<>();
		this.columnNameToRangeSettings = new HashMap<>();

	}


	public void showDialog( JTable table, SelectionColoringModel< T > selectionColoringModel )
	{
		final String[] columnNames = TableUtils.getColumnNamesAsArray( table );
		final String[] coloringModes = new String[]
				{
						LINEAR_BLUE_WHITE_RED,
						RANDOM_GLASBEY
				};

		final GenericDialog gd = new GenericDialog( "Color by Column" );

		if ( selectedColumnName == null ) selectedColumnName = columnNames[ 0 ];
		gd.addChoice( "Column", columnNames, selectedColumnName );

		if ( selectedColoringMode == null ) selectedColoringMode = coloringModes[ 0 ];
		gd.addChoice( "Coloring Mode", coloringModes, selectedColoringMode );

		gd.showDialog();
		if ( gd.wasCanceled() ) return;

		selectedColumnName = gd.getNextChoice();
		selectedColoringMode = gd.getNextChoice();

		final double[] valueRange = getValueRange( table, selectedColumnName );

		double[] valueSettings = getValueSettings( selectedColumnName, valueRange );

		switch ( selectedColoringMode )
		{
			case LINEAR_BLUE_WHITE_RED:
				colorLinear( selectionColoringModel, valueRange, valueSettings );
				break;
			case RANDOM_GLASBEY:
				colorCategorical( selectionColoringModel );
				break;
		}

	}

	private void colorCategorical( SelectionColoringModel< T > selectionColoringModel )
	{
		final CategoryTableRowColumnColoringModel< T > coloringModel
				= new CategoryTableRowColumnColoringModel< >(
				selectedColumnName,
				new GlasbeyARGBLut( 255 ) );

		selectionColoringModel.setWrappedColoringModel( coloringModel );
	}

	private void colorLinear(
			SelectionColoringModel< T > selectionColoringModel,
			double[] valueRange,
			double[] valueSettings )
	{
		final NumericTableRowColumnColoringModel< T > coloringModel
				= new NumericTableRowColumnColoringModel< >(
				selectedColumnName,
				new BlueWhiteRedARGBLut( 1000 ),
				valueSettings,
				valueRange
		);

		selectionColoringModel.setWrappedColoringModel( coloringModel );

		SwingUtilities.invokeLater( () ->
				new NumericColoringModelDialog( selectedColumnName, coloringModel, valueRange ) );
	}


	private double[] getValueSettings( String columnName, double[] valueRange )
	{
		double[] valueSettings;

		if ( columnNameToRangeSettings.containsKey( columnName ) )
			valueSettings = columnNameToRangeSettings.get( columnName );
		else
			valueSettings = valueRange.clone();

		columnNameToRangeSettings.put( columnName, valueSettings );

		return valueSettings;
	}

	private double[] getValueRange( JTable table, String column )
	{
		if ( ! columnNameToMinMax.containsKey( column ) )
		{
			final double[] minMaxValues = TableUtils.minMax( column, table );
			columnNameToMinMax.put( column, minMaxValues );
		}

		return columnNameToMinMax.get( column );
	}

}

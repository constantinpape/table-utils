package de.embl.cba.tables.objects;

import bdv.util.Bdv;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.argbconversion.SelectableRealVolatileARGBConverter;
import de.embl.cba.bdv.utils.lut.ARGBLut;
import de.embl.cba.bdv.utils.lut.LinearMappingARGBLut;
import de.embl.cba.bdv.utils.lut.Luts;
import de.embl.cba.bdv.utils.lut.StringMappingRandomARGBLut;
import de.embl.cba.tables.Logger;
import de.embl.cba.tables.TableUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.TreeMap;


public class ObjectTablePanel extends JPanel
{
	public static final String NO_COLUMN_SELECTED = "No column selected";
	public static final Integer NO_COLUMN_SELECTED_INDEX = -1;

	final private JTable table;
    private JFrame frame;
    private JScrollPane scrollPane;
    private JMenuBar menuBar;
    private HashMap< ObjectCoordinate, Integer > objectCoordinateColumnIndexMap;

    private Bdv bdv;
	private SelectableRealVolatileARGBConverter converter;
	private ARGBLut originalARGBLut; // to revert to if needed

	public ObjectTablePanel( JTable table )
    {
        super( new GridLayout(1, 0 ) );
        this.table = table;
        init();
    }

	public ObjectTablePanel( JTable table, Bdv bdv, SelectableRealVolatileARGBConverter converter )
	{
		super( new GridLayout(1, 0 ) );
		this.table = table;
		this.bdv = bdv;
		this.converter = converter;
		this.originalARGBLut = converter.getARGBLut();
		init();
	}

	private void init()
    {
        table.setPreferredScrollableViewportSize( new Dimension(500, 200) );
        table.setFillsViewportHeight( true );
        table.setAutoCreateRowSorter( true );
        table.setRowSelectionAllowed( true );

        scrollPane = new JScrollPane( table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.add( scrollPane );
        table.setAutoResizeMode( JTable.AUTO_RESIZE_OFF );

        initCoordinateColumns();

        initMenus();
    }

	public synchronized void setCoordinateColumnIndex( ObjectCoordinate objectCoordinate, Integer columnIndex )
	{
		objectCoordinateColumnIndexMap.put( objectCoordinate, columnIndex );
	}

	public int getCoordinateColumnIndex( ObjectCoordinate objectCoordinate )
	{
		return objectCoordinateColumnIndexMap.get( objectCoordinate );
	}

    private void initCoordinateColumns()
    {
        this.objectCoordinateColumnIndexMap = new HashMap<>( );

        for ( ObjectCoordinate objectCoordinate : ObjectCoordinate.values() )
        {
            objectCoordinateColumnIndexMap.put( objectCoordinate, NO_COLUMN_SELECTED_INDEX );
        }
    }

    private void initMenus()
    {
        menuBar = new JMenuBar();

        menuBar.add( getFileMenuItem() );

		menuBar.add( getObjectCoordinateMenuItem() );

		menuBar.add( getColoringMenuItem() );

	}

	private JMenu getColoringMenuItem()
	{
		JMenu coloringMenu = new JMenu( "Coloring" );

		coloringMenu.add( getRestoreOriginalColorMenuItem() );

		for ( int col = 0; col < table.getColumnCount(); col++ )
		{
			coloringMenu.add( getColorByColumnMenuItem( col ) );
		}

		return coloringMenu;
	}

	private JMenuItem getColorByColumnMenuItem( final int colorByColumn )
	{
		final JMenuItem colorByColumnMenuItem = new JMenuItem( "Color by " + table.getColumnName( colorByColumn ) );

		colorByColumnMenuItem.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				if ( converter == null )
				{
					Logger.warn( "No associated label image found." );
					return;
				}

				if ( ! isCoordinateColumnSet( ObjectCoordinate.Label ) )
				{
					Logger.warn( "Please specify the object label column:\n" +
							"[ Objects > Select coordinates... ]" );
					return;
				}

				final Object valueAt = table.getValueAt( 0, colorByColumn );

				if ( valueAt instanceof Number )
				{
					final LinearMappingARGBLut linearMappingARGBLut = getLinearMappingARGBLut( colorByColumn );

					converter.setARGBLut( linearMappingARGBLut );
				}
				else if ( valueAt instanceof String )
				{
					final StringMappingRandomARGBLut stringMappingRandomARGBLut = getStringMappingRandomARGBLut( colorByColumn );

					converter.setARGBLut( stringMappingRandomARGBLut );
				}
				else
				{
					Logger.warn( "Column types must be Number or String");
					return;
				}

				BdvUtils.repaint( bdv );
			}

		} );

		return colorByColumnMenuItem;
	}

	// TODO: for huge tables below code should be implemented more efficiently
	public LinearMappingARGBLut getLinearMappingARGBLut( int colorByColumn )
	{
		// determine mapping of object label to selected color-column
		//
		TreeMap< Double, Number > labelValueMap =
				TableUtils.columnsAsTreeMap(
						table,
						objectCoordinateColumnIndexMap.get( ObjectCoordinate.Label ),
						colorByColumn );

		// determine min and max of color-column for setting up the LUT
		//
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for ( Number value : labelValueMap.values() )
		{
			if ( value.doubleValue() < min ) min = value.doubleValue();
			if ( value.doubleValue() > max ) max = value.doubleValue();
		}

		// set up LUT
		//
		return new LinearMappingARGBLut(
				labelValueMap,
				Luts.BLUE_WHITE_RED,
				min,
				max
		);
	}


	public StringMappingRandomARGBLut getStringMappingRandomARGBLut( int colorByColumn )
	{
		// determine mapping of object label to selected color-column
		//
		TreeMap< Double, String > labelStringMap =
				TableUtils.columnsAsTreeMap(
						table,
						objectCoordinateColumnIndexMap.get( ObjectCoordinate.Label ),
						colorByColumn );

		// set up LUT
		//
		return new StringMappingRandomARGBLut(
				labelStringMap,
				Luts.BLUE_WHITE_RED
		);
	}

	private JMenuItem getRestoreOriginalColorMenuItem()
	{
		final JMenuItem restoreOriginalColorMenuItem = new JMenuItem( "Restore original coloring");

		restoreOriginalColorMenuItem.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				converter.setARGBLut( originalARGBLut );
			}
		} );
		return restoreOriginalColorMenuItem;
	}


	private JMenu getFileMenuItem()
    {
        JMenu fileMenu = new JMenu( "File" );
        final JMenuItem saveMenuItem = new JMenuItem( "Save as..." );
        saveMenuItem.addActionListener( new ActionListener()
        {
            @Override
            public void actionPerformed( ActionEvent e )
            {
                try
                {
                    TableUtils.saveTableUI( table );
                }
                catch ( IOException e1 )
                {
                    e1.printStackTrace();
                }
            }
        } );
        fileMenu.add( saveMenuItem );
        return fileMenu;
    }

	private JMenu getObjectCoordinateMenuItem()
	{
		JMenu menu = new JMenu( "Objects" );

		final ObjectTablePanel objectTablePanel = this;

		final JMenuItem coordinatesMenuItem = new JMenuItem( "Select coordinates..." );
		coordinatesMenuItem.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				new ObjectCoordinateColumnsSelectionUI( objectTablePanel );
			}
		} );


		menu.add( coordinatesMenuItem );
		return menu;
	}

    public void showPanel() {

        //Create and set up the window.
        frame = new JFrame("Table");

        frame.setJMenuBar( menuBar );

        //Show the table
        //frame.add( scrollPane );

        //Create and set up the content pane.
        this.setOpaque(true); //content panes must be opaque
        frame.setContentPane(this);

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

//    // TODO: Remove to a listener
//    public void markSelectedObjectInImagePlus( double x, double y, double z, double t )
//    {
//        PointRoi pointRoi = new PointRoi( x, y );
//        pointRoi.setPosition( 0, (int) z, (int) t );
//        pointRoi.setSize( 4 );
//        pointRoi.setStrokeColor( Color.MAGENTA );
//
//        imagePlus.setPosition( 1, (int) z, (int) t );
//        imagePlus.setRoi( pointRoi );
//    }

    public int getSelectedRowIndex()
    {
        return table.convertRowIndexToModel( table.getSelectedRow() );
    }

    public boolean isCoordinateColumnSet( ObjectCoordinate objectCoordinate )
    {
        if( objectCoordinateColumnIndexMap.get( objectCoordinate ) == NO_COLUMN_SELECTED_INDEX ) return false;
        return true;
    }

    public double getObjectCoordinate( ObjectCoordinate objectCoordinate, int row )
    {
        if ( objectCoordinateColumnIndexMap.get( objectCoordinate ) != NO_COLUMN_SELECTED_INDEX )
        {
            final int columnIndex = table.getColumnModel().getColumnIndex( objectCoordinateColumnIndexMap.get( objectCoordinate ) );
            return ( Double ) table.getValueAt( row, columnIndex );
        }
        else
        {
            return 0;
        }
    }

    public DefaultTableModel getTableModel()
    {
        return ( DefaultTableModel ) table.getModel();
    }

	public JTable getTable()
	{
		return table;
	}


}
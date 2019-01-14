package de.embl.cba.tables.objects;

import de.embl.cba.tables.Logger;
import de.embl.cba.tables.TableUtils;
import de.embl.cba.tables.TableUIs;
import de.embl.cba.tables.models.ColumnClassAwareTableModel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 *
 *
 * Notes:
 * - https://coderanch.com/t/345383/java/JTable-Paging
 */

public class ObjectTablePanel extends JPanel
{
	final private JTable table;

	final String name;
	public static final String NO_COLUMN_SELECTED = "No column selected";

	private final TableModel model;
	private JFrame frame;
    private JScrollPane scrollPane;
    private JMenuBar menuBar;
    private HashMap< ObjectCoordinate, String > objectCoordinateColumnMap;
	private ConcurrentHashMap< Double, Integer > labelRowMap;
	private HashMap< String, double[] > columnsMinMaxMap;

	public ObjectTablePanel( JTable table )
	{
		super( new GridLayout(1, 0 ) );
		this.table = table;
		this.name = "Table";
		init();
		model = table.getModel();
	}

	public ObjectTablePanel( JTable table, String name )
    {
        super( new GridLayout(1, 0 ) );
        this.table = table;
		this.name = name;
		init();
		model = table.getModel();
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

		columnsMinMaxMap = new HashMap<>();

        initCoordinateColumns();

        initMenuBar();
    }

	private void initMenuBar()
	{
		menuBar = new JMenuBar();

		menuBar.add( createTableMenuItem() );

		menuBar.add( createObjectCoordinateMenuItem() );
	}


	public synchronized void setCoordinateColumn( ObjectCoordinate objectCoordinate, String column )
	{
		if ( ! getColumnNames().contains( column ) )
		{
			Logger.error( column + " does not exist." );
			return;
		}

		objectCoordinateColumnMap.put( objectCoordinate, column );
	}

	public String getCoordinateColumn( ObjectCoordinate objectCoordinate )
	{
		return objectCoordinateColumnMap.get( objectCoordinate );
	}

    private void initCoordinateColumns()
    {
        this.objectCoordinateColumnMap = new HashMap<>( );

        for ( ObjectCoordinate objectCoordinate : ObjectCoordinate.values() )
        {
            objectCoordinateColumnMap.put( objectCoordinate, NO_COLUMN_SELECTED );
        }
    }


	public void addMenu( JMenuItem menuItem )
	{
		menuBar.add( menuItem );

		if ( frame != null ) SwingUtilities.updateComponentTreeUI( frame );
	}

	private JMenu createTableMenuItem()
    {
        JMenu menu = new JMenu( "Table" );

        menu.add( createSaveAsMenuItem() );

		menu.add( addColumnMenuItem() );

		return menu;
    }

	private JMenuItem createSaveAsMenuItem()
	{
		final JMenuItem menuItem = new JMenuItem( "Save as..." );
		menuItem.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				try
				{
					TableUIs.saveTableUI( table );
				}
				catch ( IOException e1 )
				{
					e1.printStackTrace();
				}
			}
		} );
		return menuItem;
	}

	private JMenuItem addColumnMenuItem()
	{
		final JMenuItem menuItem = new JMenuItem( "Add column..." );

		final ObjectTablePanel objectTablePanel = this;
		menuItem.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				TableUIs.addColumnUI( objectTablePanel );
			}
		} );

		return menuItem;
	}


	private JMenu createObjectCoordinateMenuItem()
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
        frame = new JFrame( name );

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

    public int getSelectedRowIndex()
    {
        return table.convertRowIndexToModel( table.getSelectedRow() );
    }

    public boolean isCoordinateColumnSet( ObjectCoordinate objectCoordinate )
    {
        if( objectCoordinateColumnMap.get( objectCoordinate ) == NO_COLUMN_SELECTED ) return false;
        return true;
    }

    public Double getObjectCoordinate( ObjectCoordinate objectCoordinate, int row )
    {
        if ( objectCoordinateColumnMap.get( objectCoordinate ) != NO_COLUMN_SELECTED )
        {
            final int columnIndex = table.getColumnModel().getColumnIndex( objectCoordinateColumnMap.get( objectCoordinate ) );
            return ( Double ) table.getValueAt( row, columnIndex );
        }
        else
        {
            return null;
        }
    }

	public void addColumn( String column, Object defaultValue )
	{
		if ( model instanceof ColumnClassAwareTableModel )
		{
			((ColumnClassAwareTableModel ) model ).addColumnClass( defaultValue );
		}

		if ( model instanceof DefaultTableModel )
		{
			final Object[] rows = new Object[ model.getRowCount() ];
			Arrays.fill( rows, defaultValue );
			((DefaultTableModel) model ).addColumn( column, rows );
		}
	}

	public ArrayList< String > getColumnNames()
	{
		return TableUtils.getColumnNames( table );
	}

	public JTable getTable()
	{
		return table;
	}


	private void createLabelRowMap()
	{
		labelRowMap = new ConcurrentHashMap();

		final int labelColumnIndex =
				table.getColumnModel().getColumnIndex( getCoordinateColumn( ObjectCoordinate.Label ) );

		final int rowCount = table.getRowCount();
		for ( int row = 0; row < rowCount; row++ )
		{
			labelRowMap.put(
					( double ) table.getValueAt( row, labelColumnIndex ),
					( int ) row );
		}
	}

	public ConcurrentHashMap< Double, Object > getLabelHashMap( String column1 )
	{
		final ConcurrentHashMap map = new ConcurrentHashMap();

		final int labelColumnIndex0 = table.getColumnModel().getColumnIndex( getCoordinateColumn( ObjectCoordinate.Label ) );
		final int labelColumnIndex1 = table.getColumnModel().getColumnIndex( column1 );

		final int rowCount = table.getRowCount();

		for ( int row = 0; row < rowCount; row++ )
		{
			map.put( (Double) getValueAt( row, labelColumnIndex0 ), getValueAt( row, labelColumnIndex1 ));
		}

		return map;
	}

	private synchronized Object getValueAt( int row, int col )
	{
		return table.getValueAt( row, col );
	}

	public int getRowIndex( double objectLabel )
	{
		if ( labelRowMap == null ) createLabelRowMap();

		return labelRowMap.get( objectLabel );
	}

	public double[] getMinMaxValues( String selectedColumn )
	{
		if ( ! columnsMinMaxMap.containsKey( selectedColumn ) )
		{
			determineMinMaxValues( selectedColumn );
		}

		return columnsMinMaxMap.get( selectedColumn );
	}

	public void determineMinMaxValues( String selectedColumn )
	{
		final int columnIndex =
				table.getColumnModel().getColumnIndex( selectedColumn );

		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		final int rowCount = table.getRowCount();
		for ( int row = 0; row < rowCount; row++ )
		{
			final double value = ( Double ) table.getValueAt( row, columnIndex );
			if ( value < min ) min = value;
			if ( value > max ) max = value;
		}

		columnsMinMaxMap.put( selectedColumn, new double[]{ min, max } );
	}

}

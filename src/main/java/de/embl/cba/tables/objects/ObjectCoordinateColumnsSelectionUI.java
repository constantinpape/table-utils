package de.embl.cba.tables.objects;

import de.embl.cba.tables.modelview.views.SegmentsTableView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;


import static de.embl.cba.tables.SwingUtils.horizontalLayoutPanel;

public class ObjectCoordinateColumnsSelectionUI extends JPanel
{
	private final SegmentsTableView objectTablePanel;

	private ArrayList< String > choices;
	private JFrame frame;
	private static Point frameLocation;

	public ObjectCoordinateColumnsSelectionUI( ObjectTablePanel objectTablePanel )
	{
		this.objectTablePanel = null;

		this.setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );

		initColumnChoices();

		for ( SegmentCoordinate coordinate : SegmentCoordinate.values())
		{
			addColumnSelectionUI( this, coordinate );
		}

		addOKButton();

		showUI();
	}

	public ObjectCoordinateColumnsSelectionUI( SegmentsTableView objectTablePanel )
	{
		this.objectTablePanel = objectTablePanel;

		this.setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );

		initColumnChoices();

		for ( SegmentCoordinate coordinate : SegmentCoordinate.values())
		{
			addColumnSelectionUI( this, coordinate );
		}

		addOKButton();

		showUI();
	}

	private void addOKButton()
	{
		final JButton okButton = new JButton( "OK" );
		okButton.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				frameLocation = frame.getLocation();
				frame.dispose();
			}
		} );

		add( okButton );
	}

	private void initColumnChoices()
	{
		choices = new ArrayList<>( );
		choices.add( ObjectTablePanel.NO_COLUMN_SELECTED );
		choices.addAll( objectTablePanel.getColumnNames() );
	}

	private void addColumnSelectionUI( final JPanel panel, final SegmentCoordinate coordinate )
	{
		final JPanel horizontalLayoutPanel = horizontalLayoutPanel();

		horizontalLayoutPanel.add( new JLabel( coordinate.toString() ) );

		final JComboBox jComboBox = new JComboBox();
		horizontalLayoutPanel.add( jComboBox );

		for ( String choice : choices )
		{
			jComboBox.addItem( choice );
		}

		// +1 is due to the option to select no getFeature
		jComboBox.setSelectedItem( objectTablePanel.getCoordinateColumn( coordinate ) );

		jComboBox.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				objectTablePanel.setCoordinateColumn( coordinate, ( String ) jComboBox.getSelectedItem() );
			}
		} );

		panel.add( horizontalLayoutPanel );
	}


	private void showUI()
	{
		//Create and set up the window.
		frame = new JFrame("Object coordinates");

		//Create and set up the content pane.
		this.setOpaque(true); //content panes must be opaque
		frame.setContentPane(this);

		//Display the window.
		frame.pack();
		if ( frameLocation != null ) frame.setLocation( frameLocation );
		frame.setVisible( true );
	}



}

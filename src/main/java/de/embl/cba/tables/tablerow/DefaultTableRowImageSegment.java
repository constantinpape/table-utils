package de.embl.cba.tables.tablerow;

import de.embl.cba.tables.imagesegment.ImageSegment;
import net.imglib2.FinalRealInterval;

import java.util.LinkedHashMap;


public class DefaultTableRowImageSegment implements TableRowImageSegment
{
	private final TableRow tableRow;
	private final ImageSegment imageSegment;

	public DefaultTableRowImageSegment( ImageSegment imageSegment,
										TableRow tableRow )
	{
		this.imageSegment = imageSegment;
		this.tableRow = tableRow;
	}

	@Override
	public String imageId()
	{
		return imageSegment.imageId();
	}

	@Override
	public double labelId()
	{
		return imageSegment.labelId();
	}

	@Override
	public int timePoint()
	{
		return imageSegment.timePoint();
	}

	@Override
	public FinalRealInterval boundingBox()
	{
		return imageSegment.boundingBox();
	}

	@Override
	public void setBoundingBox( FinalRealInterval boundingBox )
	{
		imageSegment.setBoundingBox( boundingBox );
	}

	@Override
	public float[] getMesh()
	{
		return imageSegment.getMesh();
	}

	@Override
	public void setMesh( float[] mesh )
	{
		imageSegment.setMesh( mesh );
	}

	@Override
	public LinkedHashMap< String, Object > cells()
	{
		return tableRow.cells();
	}

	@Override
	public int rowIndex()
	{
		return tableRow.rowIndex();
	}

	@Override
	public void localize( float[] position )
	{
		imageSegment.localize( position );
	}

	@Override
	public void localize( double[] position )
	{
		imageSegment.localize( position );
	}

	@Override
	public float getFloatPosition( int d )
	{
		return imageSegment.getFloatPosition( d );
	}

	@Override
	public double getDoublePosition( int d )
	{
		return imageSegment.getDoublePosition( d );
	}

	@Override
	public int numDimensions()
	{
		return imageSegment.numDimensions();
	}
}

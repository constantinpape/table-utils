package de.embl.cba.tables.modelview.views;

import bdv.viewer.TimePointListener;
import de.embl.cba.tables.modelview.coloring.ColoringModel;
import de.embl.cba.tables.modelview.datamodels.AnnotatedSegmentsModel;
import de.embl.cba.tables.modelview.datamodels.ImageSegmentsModel;
import de.embl.cba.tables.modelview.objects.AnnotatedImageSegment;
import de.embl.cba.tables.modelview.objects.ImageSegment;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class ImageSegmentLabelsARGBConverter< T extends ImageSegment >
		implements Converter< RealType, VolatileARGBType >, TimePointListener
{
	private final ImageSegmentsModel< T > segmentsModel;
	private final ColoringModel< T > coloringModel;

	private int timePointIndex;

	public ImageSegmentLabelsARGBConverter(
			ImageSegmentsModel< T > segmentsModel,
			ColoringModel< T > coloringModel )
	{
		this.segmentsModel = segmentsModel;
		this.coloringModel = coloringModel;
		timePointIndex = 0;
	}

	@Override
	public void convert( RealType label, VolatileARGBType color )
	{
		if ( label instanceof Volatile )
		{
			if ( ! ( ( Volatile ) label ).isValid() )
			{
				color.setValid( false );
				return;
			}
		}

		if ( label.getRealDouble() == 0 )
		{
			color.setValid( true );
			color.set( 0 );
			return;
		}

		coloringModel.convert(
				getAnnotatedSegment( label, timePointIndex ),
				color.get() );

	}

	public T getAnnotatedSegment( RealType label, int timePointIndex )
	{
		return ( T ) segmentsModel.getSegment(
				label.getRealDouble(),
				timePointIndex );
	}

	@Override
	public void timePointChanged( int timePointIndex )
	{
		this.timePointIndex = timePointIndex;
	}
}

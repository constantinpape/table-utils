package de.embl.cba.tables.modelview.objects;

import de.embl.cba.bdv.utils.selection.Segment;
import net.imglib2.RealInterval;
import java.util.ArrayList;

public class DefaultSegmentWithFeatures implements SegmentWithFeatures
{
	private final ArrayList< String > featureNames;
	private final Object[] featureValues;
	private final Segment segment;

	public DefaultSegmentWithFeatures( Segment segment,
									   ArrayList< String > featureNames,
									   Object[] featureValues )
	{
		this.segment = segment;
		this.featureNames = featureNames;
		this.featureValues = featureValues;
	}

	@Override
	public ArrayList< String > featureNames()
	{
		return featureNames;
	}

	@Override
	public Object featureValue( String featureName )
	{
		return featureValues[ featureNames.indexOf( featureName ) ];
	}

	@Override
	public Object[] getFeatureValues()
	{
		return featureValues;
	}

	@Override
	public double getLabel()
	{
		return segment.getLabel();
	}

	@Override
	public int getTimePoint()
	{
		return segment.getTimePoint();
	}

	@Override
	public double[] getPosition()
	{
		return segment.getPosition();
	}

	@Override
	public RealInterval getBoundingBox()
	{
		return segment.getBoundingBox();
	}



}

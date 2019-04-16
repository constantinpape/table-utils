package de.embl.cba.tables.imagesegment;

import de.embl.cba.tables.image.ImageSourcesModel;

public interface ImagesAndSegmentsModel< T extends ImageSegment >
{
	T getSegment( String imageSetName, Double label, int timePoint );

	ImageSourcesModel getImageSourcesModel();
}

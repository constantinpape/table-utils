package de.embl.cba.tables.image;

import java.util.Map;

public interface ImageSourcesModel
{
	// TODO: make it rather implement a map?
	Map< String, SourceAndMetadata< ? > > sources();

	boolean is2D();
}

package de.embl.cba.tables.modelview.datamodels;

import bdv.viewer.Source;

public class SourceAndMetadata
{
	private final Source< ? > source;
	private final Metadata metadata;

	public SourceAndMetadata( Source< ? > source, Metadata metadata )
	{
		this.source = source;
		this.metadata = metadata;
	}

	public Source< ? > getSource()
	{
		return source;
	}

	public Metadata getMetadata()
	{
		return metadata;
	}
}

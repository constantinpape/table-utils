package command;

import de.embl.cba.tables.command.ExploreLabelImageCommand;
import ij.IJ;
import net.imagej.ImageJ;

public class RunExploreLabelImageCommand
{
	public static void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		IJ.open( RunExploreMorphoLibJ2DSegmentationCommand.class.getResource(
				"blobs.zip" ).getFile() );

		IJ.open( RunExploreMorphoLibJ2DSegmentationCommand.class.getResource(
				"mask-lbl.zip" ).getFile() );

		ij.command().run( ExploreLabelImageCommand.class, true );
	}
}


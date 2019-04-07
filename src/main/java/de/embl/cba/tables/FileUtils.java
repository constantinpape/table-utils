package de.embl.cba.tables;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtils
{
	public static List< File > getFileList(
			File directory,
			String fileNameRegExp,
			boolean recursive )
	{
		final ArrayList< File > files = new ArrayList<>();

		populateFileList(
				directory,
				fileNameRegExp,
				files,
				recursive );

		return files;
	}

	public static void populateFileList(
			File directory,
			String fileNameRegExp,
			List< File > files,
			boolean recursive ) {

		// Get all the files from a directory.
		File[] fList = directory.listFiles();

		if( fList != null )
		{
			for ( File file : fList )
			{
				if ( file.isFile() )
				{
					final Matcher matcher = Pattern.compile( fileNameRegExp ).matcher( file.getName() );

					if ( matcher.matches() )
					{
						files.add( file );
					}

				}
				else if ( file.isDirectory() )
				{
					if ( recursive )
						populateFileList( file, fileNameRegExp, files, recursive );
				}
			}
		}
	}

	public static boolean stringContainsItemFromList( String inputStr, ArrayList< String > items)
	{
		return items.parallelStream().anyMatch( inputStr::contains );
	}
}

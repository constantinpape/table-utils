package de.embl.cba.tables.modelview.segments;

import java.util.Map;

public interface TableRowMap extends Map< String, Object >
{
	/**
	 * The index of the row in the underlying table.
	 *
	 * @return row index
	 */
	int rowIndex();
}

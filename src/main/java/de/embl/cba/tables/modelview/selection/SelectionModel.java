package de.embl.cba.tables.modelview.selection;

import de.embl.cba.tables.modelview.selection.Listeners;
import de.embl.cba.tables.modelview.selection.SelectionListener;

import java.util.Collection;
import java.util.Set;

public interface SelectionModel< T >
{
	/**
	 * Get the selected state of a object.
	 *
	 * @param object
	 *            a object.
	 * @return {@code true} if specified object is selected.
	 */
	public boolean isSelected( final T object );

	/**
	 * Sets the selected state of a object.
	 *
	 * @param object
	 *            a object.
	 * @param selected
	 *            selected state to set for specified object.
	 */
	public void setSelected( final T object, final boolean select );
	
	/**
	 * Toggles the selected state of a object.
	 *
	 * @param object
	 *            a object.
	 */
	public void toggle( final T object );


	/**
	 * Focus on an object without changing its selection state.
	 *
	 * @param object
	 *            a object.
	 */
	public void focus( final T object );


	/**
	 * Get the focus state of an object
	 *
	 * @param object
	 *            a object.
	 */
	public boolean isFocused( final T object );

	/**
	 * Sets the selected state of a collection of segments.
	 *
	 * @param objects
	 *            the object collection.
	 * @param selected
	 *            selected state to set for specified object collection.
	 * @return {@code true} if the selection was changed by this call.
	 */
	public boolean setSelected( final Collection< T > objects, final boolean select );



	/**
	 * Clears this selection.
	 *
	 * @return {@code true} if this selection was not empty prior to
	 *         calling this method.
	 */
	public boolean clearSelection();

	/**
	 * Get the selected segments.
	 **
	 * @return a <b>new</b> {@link Set} containing all the selected segments.
	 */
	public Set< T > getSelected();

	public boolean isEmpty();

	/**
	 * Get the list of selection listeners. Add a {@link SelectionListener} to
	 * this list, for being notified when the object/edge selection changes.
	 *
	 * @return the list of listeners
	 */
	public Listeners< SelectionListener > listeners();

	public void resumeListeners();

	public void pauseListeners();
}


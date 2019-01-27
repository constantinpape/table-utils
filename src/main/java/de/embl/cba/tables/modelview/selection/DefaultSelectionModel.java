package de.embl.cba.tables.modelview.selection;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DefaultSelectionModel< T > implements SelectionModel< T >
{
	private final Listeners.SynchronizedList< SelectionListener > listeners;
	private final Set< T > selected;

	public DefaultSelectionModel()
	{
		listeners = new Listeners.SynchronizedList<>(  );

		selected = new HashSet();
	}


	@Override
	public boolean isSelected( T object )
	{
		return selected.contains( object );
	}

	@Override
	public void setSelected( T object, boolean select )
	{
		if ( select )
		{
			add( object );
		}
		else
		{
			remove( object );
		}
	}

	private void remove( T object )
	{
		if ( selected.contains( object ) )
		{
			selected.remove( object );

			for ( SelectionListener listener : listeners.list )
			{
				new Thread( new Runnable()
				{
					@Override
					public void run()
					{
						listener.selectionChanged();
						listener.selectionEvent( object, false );
					}
				}).start();
			}
		}
	}

	private void add( T object )
	{
		if ( ! selected.contains( object ) )
		{
			selected.add( object );

			for ( SelectionListener listener : listeners.list )
			{
				new Thread( new Runnable()
				{
					@Override
					public void run()
					{
						listener.selectionChanged();
						listener.selectionEvent( object, true );
					}
				}).start();
			}
		}
	}

	@Override
	public void toggle( T object )
	{
		if ( selected.contains( object ) )
		{
			remove( object );
		}
		else
		{
			add( object );
		}
	}

	@Override
	public boolean setSelected( Collection< T > objects, boolean select )
	{
		return false;
	}

	@Override
	public boolean clearSelection()
	{
		if ( selected.size() == 0 )
		{
			return false;
		}
		else
		{
			selected.clear();

			for ( SelectionListener listener : listeners.list )
			{
				new Thread( new Runnable()
				{
					@Override
					public void run()
					{
						listener.selectionChanged();
					}
				}).start();
			}
			return true;
		}
	}

	@Override
	public Set< T > getSelected()
	{
		return selected;
	}

	@Override
	public boolean isEmpty()
	{
		return selected.isEmpty();
	}

	@Override
	public Listeners< SelectionListener > listeners()
	{
		return listeners;
	}

	@Override
	public void resumeListeners()
	{

	}

	@Override
	public void pauseListeners()
	{

	}

}

/**
 * Copyright (c) 2005, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia development team" nor the names of
 *      its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;

import java.util.HashMap;
import java.util.StringTokenizer;

import net.java.dev.spellcast.utilities.DataUtilities;
import net.java.dev.spellcast.utilities.UtilityConstants;

/**
 * A static class which retrieves all the tradeable items available in
 * the Kingdom of Loathing and allows the client to do item look-ups.
 * The item list being used is a parsed and resorted list found on
 * Ohayou's Kingdom of Loathing website.  In order to decrease server
 * load, this item list is stored within the JAR archive.
 */

public class TradeableItemDatabase implements UtilityConstants
{
	private static final String ITEM_DBASE_FILE = "tradeitems.dat";
	private static final int ITEM_COUNT = 1000;

	private static String [] itemByID = new String[ ITEM_COUNT ];
	private static HashMap itemByName = new HashMap( ITEM_COUNT );

	static
	{
		// This begins by opening up the data file and preparing
		// a buffered reader; once this is done, every line is
		// examined and double-referenced: once in the name-lookup,
		// and again in the ID lookup.

		BufferedReader itemdata = DataUtilities.getReaderForSharedDataFile( ITEM_DBASE_FILE );

		try
		{
			String line;
			while ( (line = itemdata.readLine()) != null )
			{
				StringTokenizer strtok = new StringTokenizer( line, "\t" );
				if ( strtok.countTokens() == 2 )
				{
					int itemID = Integer.parseInt( strtok.nextToken() );
					String itemName = strtok.nextToken();

					itemByID[ itemID ] = itemName;
					itemByName.put( itemName, new Integer( itemID ) );
				}
			}
		}
		catch ( IOException e )
		{
			// If an IOException is thrown, that means there was
			// a problem reading in the appropriate data file;
			// that means that no item database exists.  This
			// exception is strange enough that it won't be
			// handled at the current time.
		}
	}

	/**
	 * Returns the ID number for an item, given its name.
	 * @param	itemName	The name of the item to lookup
	 * @return	The ID number of the corresponding item
	 */

	public static int getItemID( String itemName )
	{	return ((Integer)itemByName.get( itemName )).intValue();
	}

	/**
	 * Returns the name for an item, given its ID number.
	 * @param	itemID	The ID number of the item to lookup
	 * @return	The name of the corresponding item
	 */

	public static String getItemName( int itemID )
	{	return itemByID[ itemID ];
	}
}
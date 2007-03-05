/**
 * Copyright (c) 2005-2007, KoLmafia development team
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
 *  [3] Neither the name "KoLmafia" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
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

import java.io.BufferedReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ClassSkillsDatabase extends KoLDatabase
{
	private static Map skillById = new TreeMap();
	private static Map skillByName = new TreeMap();
	private static Map mpConsumptionById = new TreeMap();
	private static Map skillTypeById = new TreeMap();
	private static Map skillCategoryById = new TreeMap();
	private static Map durationById = new TreeMap();

	public static final int CASTABLE = -1;
	public static final int PASSIVE = 0;
	public static final int SKILL = 1;
	public static final int BUFF = 2;
	public static final int COMBAT = 3;

	private static final String [] CATEGORIES = new String []
	{
		"Uncategorized",
		"Item Creation",
		"Defensive (Passive)",
		"Defensive (Castable)",
		"Bonus to Weapon Damage",
		"Weapon-Based Attacks",
		"Bonus to Spellslinging",
		"Spellslinging Attacks",
		"Monster Delevelers",
		"Health Restoration",
		"Buffed Stat Adjustment",
		"RNG Hate Mitigation",
		"Stat Gains Boosters"
	};

	static
	{
		// This begins by opening up the data file and preparing
		// a buffered reader; once this is done, every line is
		// examined and float-referenced: once in the name-lookup,
		// and again in the Id lookup.

		BufferedReader reader = getReader( "classskills.txt" );

		String [] data;
		Integer skillId, skillType, skillCategory;
		Integer mpConsumption, duration;
		String skillName;

		while ( (data = readData( reader )) != null )
		{
			if ( data.length == 6 )
			{
				skillId = Integer.valueOf( data[0] );
				skillName = getDisplayName( data[1] );

				skillType = Integer.valueOf( data[2] );
				skillCategory = Integer.valueOf( data[3] );

				mpConsumption = Integer.valueOf( data[4] );
				duration = Integer.valueOf( data[5] );

				skillById.put( skillId, skillName );
				skillByName.put( getCanonicalName( skillName ), skillId );

				skillTypeById.put( skillId, skillType );
				skillCategoryById.put( skillId, skillCategory );

				mpConsumptionById.put( skillId, mpConsumption );
				durationById.put( skillId, duration );
			}
		}

		try
		{
			reader.close();
		}
		catch ( Exception e )
		{
			// This should not happen.  Therefore, print
			// a stack trace for debug purposes.

			printStackTrace( e );
		}
	}

	/**
	 * Returns a list of all skills which contain the given
	 * substring.  This is useful for people who are doing
	 * lookups on skills.
	 */

	public static final List getMatchingNames( String substring )
	{	return getMatchingNames( skillByName, substring );
	}

	/**
	 * Returns the name for an skill, given its Id.
	 * @param	skillId	The Id of the skill to lookup
	 * @return	The name of the corresponding skill
	 */

	public static final String getSkillName( int skillId )
	{	return (String) skillById.get( new Integer( skillId ) );
	}

	/**
	 * Returns the type for an skill, given its Id.
	 * @param	skillId	The Id of the skill to lookup
	 * @return	The type of the corresponding skill
	 */

	public static final int getSkillType( int skillId )
	{
		Object skillType = skillTypeById.get( new Integer( skillId ) );
		return skillType == null ? -1 : ((Integer)skillType).intValue();
	}

	public static final int getCategory( int skillId )
	{
		Object skillCategory = skillCategoryById.get( new Integer( skillId ) );
		return skillCategory == null ? 0 : ((Integer)skillCategory).intValue();
	}

	/**
	 * Returns the Id number for an skill, given its name.
	 * @param	skillName	The name of the skill to lookup
	 * @return	The Id number of the corresponding skill
	 */

	public static final int getSkillId( String skillName )
	{
		Object skillId = skillByName.get( getCanonicalName( skillName ) );
		return skillId == null ? -1 : ((Integer)skillId).intValue();
	}

	/**
	 * Returns how much MP is consumed by using the skill
	 * with the given Id.
	 *
	 * @param	skillId	The id of the skill to lookup
	 * @return	The MP consumed by the skill, or 0 if unknown
	 */

	public static final int getMPConsumptionById( int skillId )
	{
		// Summon Candy Hearts has a special mana cost.
		if ( skillId == 18 )
		{
			int count = StaticEntity.getIntegerProperty( "candyHeartSummons" );
			return Math.max( ((count + 1) * (count + 2)) / 2 + KoLCharacter.getManaCostModifier(), 1 );
		}

		// Moxious Maneuver has a special mana cost.
		if ( skillId == 7008 )
			return Math.max( KoLCharacter.getLevel() + KoLCharacter.getManaCostModifier(), 1 );

		// Magic Missile has a special mana cost.
		if ( skillId == 7009 )
			return Math.max( Math.min( ( KoLCharacter.getLevel() + 3 ) / 2, 6 ) + KoLCharacter.getManaCostModifier(), 1 );

		if ( getSkillType( skillId ) == PASSIVE )
			return 0;

		Object mpConsumption = mpConsumptionById.get( new Integer( skillId ) );
		return mpConsumption == null ? 0 : Math.max( ((Integer)mpConsumption).intValue() + KoLCharacter.getManaCostModifier(), 1 );
	}

	/**
	 * Returns how many rounds of buff are gained by using
	 * the skill with the given Id.
	 *
	 * @param	skillId	The id of the skill to lookup
	 * @return	The duration of effect the cast gives
	 */

	public static final int getEffectDuration( int skillId )
	{
		Object duration = durationById.get( new Integer( skillId ) );
		if ( duration == null )
			return 0;

		int actualDuration = ((Integer)duration).intValue();
		if ( actualDuration == 0 || getSkillType( skillId ) != BUFF )
			return actualDuration;

		if ( skillId > 6000 && skillId < 7000 && KoLCharacter.hasItem( UseSkillRequest.ROCKNROLL_LEGEND ) )
			actualDuration += 10;

		if ( KoLCharacter.hasItem( UseSkillRequest.WIZARD_HAT ) )
			actualDuration += 5;

		return actualDuration;
	}

	/**
	 * Returns whether or not this is a normal skill that can only be
	 * used on the player.
	 *
	 * @return <code>true</code> if the skill is a normal skill
	 */

	public static final boolean isNormal( int skillId )
	{	return isType( skillId, SKILL );
	}

	/**
	 * Returns whether or not the skill is a passive.
	 * @return	<code>true</code> if the skill is passive
	 */

	public static final boolean isPassive( int skillId )
	{	return isType( skillId, PASSIVE );
	}

	/**
	 * Returns whether or not the skill is a buff (ie: can be
	 * used on others).
	 *
	 * @return	<code>true</code> if the skill can target other players
	 */

	public static final boolean isBuff( int skillId )
	{	return isType( skillId, BUFF );
	}

	/**
	 * Returns whether or not the skill is a combat skill (ie: can
	 * be used while fighting).
	 *
	 * @return	<code>true</code> if the skill can be used in combat
	 */

	public static final boolean isCombat( int skillId )
	{	return isType( skillId, COMBAT );
	}

	/**
	 * Utility method used to determine if the given skill is of the
	 * appropriate type.
	 */

	private static final boolean isType( int skillId, int type )
	{
		Object skillType = skillTypeById.get( new Integer( skillId ) );
		return skillType == null ? false : ((Integer)skillType).intValue() == type;
	}

	/**
	 * Returns all skills in the database of the given type.
	 */

	public static final List getSkillsByType( int type )
	{
		ArrayList list = new ArrayList();

		boolean shouldAdd = false;
		Object [] keys = skillTypeById.keySet().toArray();

		for ( int i = 0; i < keys.length; ++i )
		{
			shouldAdd = false;

			if ( type == CASTABLE )
				shouldAdd = isType( ((Integer)keys[i]).intValue(), SKILL ) || isType( ((Integer)keys[i]).intValue(), BUFF );
			else
				shouldAdd = isType( ((Integer)keys[i]).intValue(), type );

			if ( shouldAdd )
				list.add( UseSkillRequest.getInstance( ((Integer)keys[i]).intValue() ) );
		}

		return list;
	}

	/**
	 * Returns whether or not an item with a given name
	 * exists in the database; this is useful in the
	 * event that an item is encountered which is not
	 * tradeable (and hence, should not be displayed).
	 *
	 * @return	<code>true</code> if the item is in the database
	 */

	public static final boolean contains( String skillName )
	{	return skillByName.containsKey( getCanonicalName( skillName ) );
	}

	/**
	 * Returns the set of skills keyed by name
	 * @return	The set of skills keyed by name
	 */
	public static Set entrySet()
	{	return skillById.entrySet();
	}

	public static void generateSkillList( StringBuffer buffer, boolean appendHTML )
	{
		Object [] elements = new Object[ availableSkills.size() ];
		availableSkills.toArray( elements );

		ArrayList [] categories = new ArrayList[ CATEGORIES.length ];
		for ( int i = 0; i < categories.length; ++i )
			categories[i] = new ArrayList();

		for ( int i = 0; i < elements.length; ++i )
		{
			int skillId = ((UseSkillRequest)elements[i]).getSkillId();

			int categoryId = getCategory( skillId );
			categories[ categoryId ].add( new Integer( skillId ) );
		}

		Integer currentSkill;

		for ( int i = 0; i < categories.length; ++i )
		{
			if ( categories[i].isEmpty() )
				continue;

			Collections.sort( categories[i] );

			if ( appendHTML )
				buffer.append( "<u><b>" );

			buffer.append( CATEGORIES[i] );

			if ( appendHTML )
				buffer.append( "</b></u><br>" );
			else
				buffer.append( LINE_BREAK );

			for ( int j = 0; j < categories[i].size(); ++j )
			{
				currentSkill = (Integer) categories[i].get(j);

				if ( appendHTML )
				{
					buffer.append( "<a onClick=\"javascript:skill(" );
					buffer.append( currentSkill );
					buffer.append( ");\">" );
				}
				else
				{
					buffer.append( " - " );
				}

				buffer.append( skillById.get( currentSkill ) );

				if ( appendHTML )
					buffer.append( "</a><br>" );
				else
					buffer.append( LINE_BREAK );
			}

			if ( appendHTML )
				buffer.append( "<br>" );
			else
				buffer.append( LINE_BREAK );
		}
	}
}

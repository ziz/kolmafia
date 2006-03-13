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

// layout
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;

// event listeners
import javax.swing.SwingUtilities;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

// containers
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JCheckBox;
import javax.swing.ListSelectionModel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JOptionPane;

// other imports
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Method;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.text.DecimalFormat;
import java.text.ParseException;

import net.java.dev.spellcast.utilities.PanelList;
import net.java.dev.spellcast.utilities.PanelListCell;
import net.java.dev.spellcast.utilities.LockableListModel;
import net.java.dev.spellcast.utilities.JComponentUtilities;

/**
 * An extension of <code>KoLFrame</code> which handles all the clan
 * management functionality of Kingdom of Loathing.
 */

public class ClanManageFrame extends KoLFrame
{
	private LockableListModel rankList;

	private JTabbedPane tabs;
	private ClanBuffPanel clanBuff;
	private StoragePanel storing;
	private WithdrawPanel withdrawal;
	private DonationPanel donation;
	private AttackPanel attacks;
	private WarfarePanel warfare;
	private SnapshotPanel snapshot;
	private AscensionPanel ascension;
	private MemberSearchPanel search;
	private ClanMemberPanelList results;

	public ClanManageFrame( KoLmafia client )
	{
		super( client, "Clan Management" );

		this.rankList = new LockableListModel();

		this.storing = new StoragePanel();
		this.clanBuff = new ClanBuffPanel();
		this.donation = new DonationPanel();
		this.withdrawal = new WithdrawPanel();
		this.attacks = new AttackPanel();
		this.warfare = new WarfarePanel();
		this.snapshot = new SnapshotPanel();
		this.ascension = new AscensionPanel();
		this.search = new MemberSearchPanel();
		this.tabs = new JTabbedPane();

		JPanel snapPanel = new JPanel();
		snapPanel.setLayout( new BoxLayout( snapPanel, BoxLayout.Y_AXIS ) );
		snapPanel.add( snapshot );
		snapPanel.add( ascension );

		tabs.addTab( "Clan Snapshot", snapPanel );

		JPanel karmaPanel = new JPanel( new BorderLayout() );
		karmaPanel.add( donation, BorderLayout.NORTH );

		JPanel stashPanel = new JPanel( new GridLayout( 2, 1, 10, 10 ) );
		stashPanel.add( storing );
		stashPanel.add( withdrawal );
		karmaPanel.add( stashPanel, BorderLayout.CENTER );

		tabs.addTab( "Stash Manager", karmaPanel );

		JPanel purchasePanel = new JPanel();
		purchasePanel.setLayout( new BoxLayout( purchasePanel, BoxLayout.Y_AXIS ) );
		purchasePanel.add( attacks );
		purchasePanel.add( clanBuff );
		purchasePanel.add( warfare );

		tabs.addTab( "Warfare & Buffs", purchasePanel );

		results = new ClanMemberPanelList();
		JComponent [] header = new JComponent[5];
		header[0] = new JLabel( "Member Name", JLabel.LEFT );
		header[1] = new JLabel( "Clan Rank", JLabel.LEFT );
		header[2] = new JLabel( "Clan Title", JLabel.LEFT );
		header[3] = new JLabel( "Karma", JLabel.LEFT );
		header[4] = new SelectAllForBootButton();

		JComponentUtilities.setComponentSize( header[0], 120, 20 );
		JComponentUtilities.setComponentSize( header[1], 150, 20 );
		JComponentUtilities.setComponentSize( header[2], 150, 20 );
		JComponentUtilities.setComponentSize( header[3], 80, 20 );
		JComponentUtilities.setComponentSize( header[4], 20, 20 );

		JPanel headerPanel = new JPanel();
		headerPanel.setLayout( new BoxLayout( headerPanel, BoxLayout.X_AXIS ) );
		headerPanel.add( Box.createHorizontalStrut( 25 ) );

		for ( int i = 0; i < header.length; ++i )
		{
			headerPanel.add( Box.createHorizontalStrut( 10 ) );
			headerPanel.add( header[i] );
		}

		headerPanel.add( Box.createHorizontalStrut( 5 ) );

		JPanel resultsPanel = new JPanel( new BorderLayout() );
		resultsPanel.add( headerPanel, BorderLayout.NORTH );
		resultsPanel.add( new JScrollPane( results, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS ), BorderLayout.CENTER );

		JPanel searchPanel = new JPanel( new BorderLayout() );
		searchPanel.add( search, BorderLayout.NORTH );
		searchPanel.add( resultsPanel, BorderLayout.CENTER );
		tabs.addTab( "Member Search", searchPanel );

		framePanel.setLayout( new CardLayout( 10, 10 ) );
		framePanel.add( tabs, "" );

		if ( client != null )
			(new RequestThread( new ClanStashRequest( client ) )).start();
	}

	/**
	 * An internal class which represents the panel used for clan
	 * buffs in the <code>ClanManageFrame</code>.
	 */

	private class ClanBuffPanel extends LabeledKoLPanel
	{
		private boolean isBuffing;
		private JComboBox buffField;
		private JTextField countField;

		public ClanBuffPanel()
		{
			super( "Hire Trainers", "purchase", "take break", new Dimension( 80, 20 ), new Dimension( 240, 20 ) );
			this.isBuffing = false;

			buffField = new JComboBox( ClanBuffRequest.getRequestList( client ) );
			countField = new JTextField();

			VerifiableElement [] elements = new VerifiableElement[2];
			elements[0] = new VerifiableElement( "Clan Buff: ", buffField );
			elements[1] = new VerifiableElement( "# of times: ", countField );

			setContent( elements );
		}

		public void setEnabled( boolean isEnabled )
		{
			super.setEnabled( isEnabled );
			buffField.setEnabled( isEnabled );
			countField.setEnabled( isEnabled );
		}

		protected void actionConfirmed()
		{	(new RequestThread( (Runnable) buffField.getSelectedItem(), getValue( countField ) )).start();
		}

		protected void actionCancelled()
		{
			if ( isBuffing )
				client.updateDisplay( ERROR_STATE, "Purchase attempts cancelled." );
		}
	}

	/**
	 * An internal class which represents the panel used for clan
	 * buffs in the <code>ClanManageFrame</code>.
	 */

	private class AttackPanel extends LabeledKoLPanel
	{
		private JComboBox enemyList;

		public AttackPanel()
		{
			super( "Loot Another Clan", "attack", "refresh", new Dimension( 80, 20 ), new Dimension( 240, 20 ) );
			enemyList = new JComboBox( ClanListRequest.getEnemyClans() );

			VerifiableElement [] elements = new VerifiableElement[1];
			elements[0] = new VerifiableElement( "Victim: ", enemyList );
			setContent( elements );
		}

		public void setEnabled( boolean isEnabled )
		{
			super.setEnabled( isEnabled );
			enemyList.setEnabled( isEnabled );
		}

		protected void actionConfirmed()
		{	(new RequestThread( (Runnable) enemyList.getSelectedItem() )).start();
		}

		protected void actionCancelled()
		{	(new RequestThread( new ClanListRequest( client ) )).start();
		}
	}

	private class WarfarePanel extends LabeledKoLPanel
	{
		private JTextField goodies;
		private JTextField oatmeal, recliners;
		private JTextField ground, airborne, archers;

		public WarfarePanel()
		{
			super( "Prepare for WAR!!!", "purchase", "calculate", new Dimension( 120, 20 ), new Dimension( 200, 20 ) );

			goodies = new JTextField();
			oatmeal = new JTextField();
			recliners = new JTextField();
			ground = new JTextField();
			airborne = new JTextField();
			archers = new JTextField();

			VerifiableElement [] elements = new VerifiableElement[6];
			elements[0] = new VerifiableElement( "Goodies: ", goodies );
			elements[1] = new VerifiableElement( "Oatmeal: ", oatmeal );
			elements[2] = new VerifiableElement( "Recliners: ", recliners );
			elements[3] = new VerifiableElement( "Ground Troops: ", ground );
			elements[4] = new VerifiableElement( "Airborne Troops: ", airborne );
			elements[5] = new VerifiableElement( "La-Z-Archers: ", archers );

			setContent( elements );
		}

		public void setEnabled( boolean isEnabled )
		{
			super.setEnabled( isEnabled );

			goodies.setEnabled( isEnabled );
			oatmeal.setEnabled( isEnabled );
			recliners.setEnabled( isEnabled );
			ground.setEnabled( isEnabled );
			airborne.setEnabled( isEnabled );
			archers.setEnabled( isEnabled );
		}

		public void actionConfirmed()
		{	(new RequestThread( new ClanMaterialsRequest() )).start();
		}

		public void actionCancelled()
		{
			int totalValue = getValue( goodies ) * 1000 + getValue( oatmeal ) * 3 + getValue( recliners ) * 1500 +
				getValue( ground ) * 300 + getValue( airborne ) * 500 + getValue( archers ) * 500;

			JOptionPane.showMessageDialog( null, "This purchase will cost " + totalValue + " meat" );
		}

		private class ClanMaterialsRequest extends KoLRequest
		{
			public ClanMaterialsRequest()
			{
				super( ClanManageFrame.this.client, "clan_war.php" );
				addFormField( "action", "Yep." );
				addFormField( "goodies", String.valueOf( getValue( goodies ) ) );
				addFormField( "oatmeal", String.valueOf( getValue( oatmeal ) ) );
				addFormField( "recliners", String.valueOf( getValue( recliners ) ) );
				addFormField( "grunts", String.valueOf( getValue( ground ) ) );
				addFormField( "flyers", String.valueOf( getValue( airborne ) ) );
				addFormField( "archers", String.valueOf( getValue( archers ) ) );
			}

			public void run()
			{
				client.updateDisplay( "Purchasing clan materials..." );

				super.run();

				// Theoretically, there should be a test for error state,
				// but because I'm lazy, that's not happening.

				client.updateDisplay( "Purchase request processed." );
			}
		}
	}

	/**
	 * An internal class which represents the panel used for donations to
	 * the clan coffer.
	 */

	private class DonationPanel extends KoLPanel
	{
		private JTextField amountField;

		public DonationPanel()
		{
			super( "donate meat", "loot clan", new Dimension( 80, 20 ), new Dimension( 240, 20 ) );

			amountField = new JTextField();
			VerifiableElement [] elements = new VerifiableElement[1];
			elements[0] = new VerifiableElement( "Amount: ", amountField );
			setContent( elements );
		}

		public void setEnabled( boolean isEnabled )
		{
			super.setEnabled( isEnabled );
			amountField.setEnabled( isEnabled );
		}

		protected void actionConfirmed()
		{	(new RequestThread( new ClanStashRequest( client, getValue( amountField ) ) )).start();
		}

		protected void actionCancelled()
		{	JOptionPane.showMessageDialog( null, "The Hermit beat you to it.  ARGH!" );
		}
	}

	/**
	 * Internal class used to handle everything related to
	 * placing items into the stash.
	 */

	private class StoragePanel extends ItemManagePanel
	{
		public StoragePanel()
		{	super( "Inside Inventory", "put in stash", "put in closet", KoLCharacter.getInventory() );
		}

		protected void actionConfirmed()
		{	(new RequestThread( new ClanStashRequest( client, elementList.getSelectedValues(), ClanStashRequest.ITEMS_TO_STASH ) )).start();
		}

		protected void actionCancelled()
		{	(new RequestThread( new ItemStorageRequest( client, ItemStorageRequest.INVENTORY_TO_CLOSET, elementList.getSelectedValues() ) )).start();
		}

		public void setEnabled( boolean isEnabled )
		{
			super.setEnabled( isEnabled );
			elementList.setEnabled( isEnabled );
		}
	}

	/**
	 * Internal class used to handle everything related to
	 * placing items into the stash.
	 */

	private class WithdrawPanel extends ItemManagePanel
	{
		public WithdrawPanel()
		{	super( "Inside Clan Stash", "put in bag", "refresh", ClanManager.getStash() );
		}

		protected void actionConfirmed()
		{
			Object [] items = elementList.getSelectedValues();

			// Check the rank list to see if you're one
			// of the clan administrators.

			if ( items.length > 1 && rankList.isEmpty() )
			{
				rankList = ClanManager.getRankList();

				// If it's been double-confirmed that you're
				// not a clan administrator, then tell them
				// they can't do anything with the stash.

				if ( rankList.isEmpty() )
				{
					JOptionPane.showMessageDialog( null, "Look, but don't touch." );
					return;
				}
			}

			AdventureResult selection;

			for ( int i = 0; i < items.length; ++i )
			{
				selection = (AdventureResult) items[i];
				items[i] = new AdventureResult( selection.getItemID(),
					getQuantity( "Retrieving " + selection.getName() + " from the stash...", selection.getCount() ) );
			}

			(new RequestThread( new ClanStashRequest( client, items, ClanStashRequest.STASH_TO_ITEMS ) )).start();
		}

		protected void actionCancelled()
		{	(new RequestThread( new ClanStashRequest( client ) )).start();
		}

		public void setEnabled( boolean isEnabled )
		{
			super.setEnabled( isEnabled );
			elementList.setEnabled( isEnabled );
		}
	}

	private class MemberSearchPanel extends KoLPanel
	{
		private JComboBox parameterSelect;
		private JComboBox matchSelect;
		private JTextField valueField;

		public MemberSearchPanel()
		{
			super( "search clan", "apply changes", new Dimension( 80, 20 ), new Dimension( 360, 20 ) );

			parameterSelect = new JComboBox();
			for ( int i = 0; i < ClanSnapshotTable.FILTER_NAMES.length; ++i )
				parameterSelect.addItem( ClanSnapshotTable.FILTER_NAMES[i] );

			matchSelect = new JComboBox();
			matchSelect.addItem( "Less than..." );
			matchSelect.addItem( "Equal to..." );
			matchSelect.addItem( "Greater than..." );

			valueField = new JTextField();

			VerifiableElement [] elements = new VerifiableElement[3];
			elements[0] = new VerifiableElement( "Parameter: ", parameterSelect );
			elements[1] = new VerifiableElement( "Constraint: ", matchSelect );
			elements[2] = new VerifiableElement( "Value:", valueField );

			setContent( elements, null, null, true, true );
			setDefaultButton( confirmedButton );
		}

		public void setEnabled( boolean isEnabled )
		{
			super.setEnabled( isEnabled );
			parameterSelect.setEnabled( isEnabled );
			matchSelect.setEnabled( isEnabled );
			valueField.setEnabled( isEnabled );
		}

		protected void actionConfirmed()
		{
			ClanManager.applyFilter( matchSelect.getSelectedIndex() - 1, parameterSelect.getSelectedIndex(), valueField.getText() );
			client.updateDisplay( "Search results retrieved." );
		}

		protected void actionCancelled()
		{
			client.updateDisplay( "Determining changes..." );

			List rankChange = new ArrayList();
			List newRanks = new ArrayList();

			List titleChange = new ArrayList();
			List newTitles = new ArrayList();

			List boots = new ArrayList();

			Object currentComponent;
			ClanMemberPanel currentMember;
			Object desiredRank, desiredTitle;

			for ( int i = 0; i < results.getComponentCount(); ++i )
			{
				currentComponent = results.getComponent(i);
				if ( currentComponent instanceof ClanMemberPanel )
				{
					currentMember = (ClanMemberPanel) currentComponent;
					if ( currentMember.bootCheckBox.isSelected() )
						boots.add( currentMember.memberName.getText() );

					desiredRank = currentMember.rankSelect.getSelectedItem();
					if ( desiredRank != null && !desiredRank.equals( currentMember.initialRank ) )
					{
						rankChange.add( currentMember.memberName.getText() );
						newRanks.add( String.valueOf( currentMember.rankSelect.getSelectedIndex() ) );
					}

					desiredTitle = currentMember.titleField.getText();
					if ( desiredTitle != null && !desiredTitle.equals( currentMember.initialTitle ) )
					{
						titleChange.add( currentMember.memberName.getText() );
						newTitles.add( (String) desiredTitle );
					}
				}
			}

			client.updateDisplay( "Applying changes..." );
			(new ClanMembersRequest( client, rankChange.toArray(), newRanks.toArray(), titleChange.toArray(), newTitles.toArray(), boots.toArray() )).run();
			client.updateDisplay( "Changes have been applied." );
		}
	}

	private class SelectAllForBootButton extends JButton implements ActionListener
	{
		private boolean shouldSelect;

		public SelectAllForBootButton()
		{
			super( JComponentUtilities.getSharedImage( "preferences.gif" ) );
			addActionListener( this );
			setToolTipText( "Boot" );
			shouldSelect = true;
		}

		public void actionPerformed( ActionEvent e )
		{
			Object currentComponent;
			ClanMemberPanel currentMember;

			for ( int i = 0; i < results.getComponentCount(); ++i )
			{
				currentComponent = results.getComponent(i);
				if ( currentComponent instanceof ClanMemberPanel )
				{
					currentMember = (ClanMemberPanel) currentComponent;
					currentMember.bootCheckBox.setSelected( shouldSelect );
				}
			}

			shouldSelect = !shouldSelect;
		}
	}

	public class ClanMemberPanelList extends PanelList
	{
		public ClanMemberPanelList()
		{	super( 12, 600, 30, ClanSnapshotTable.getFilteredList() );
		}

		protected PanelListCell constructPanelListCell( Object value, int index )
		{
			ClanMemberPanel toConstruct = new ClanMemberPanel( (ProfileRequest) value );
			toConstruct.updateDisplay( this, value, index );
			return toConstruct;
		}
	}

	public class ClanMemberPanel extends JPanel implements PanelListCell
	{
		private JLabel memberName;
		private JComboBox rankSelect;
		private JTextField titleField;
		private JLabel clanKarma;
		private JCheckBox bootCheckBox;

		private String initialRank, initialTitle;
		private ProfileRequest profile;

		public ClanMemberPanel( ProfileRequest value )
		{
			this.profile = value;

			memberName = new JLabel( value.getPlayerName(), JLabel.LEFT );

			if ( rankList.isEmpty() )
				rankList = ClanManager.getRankList();

			rankSelect = rankList.isEmpty() ? new JComboBox() : new JComboBox( (LockableListModel) rankList.clone() );

			// In the event that they were just searching for fun purposes,
			// there will be no ranks.  So it still looks like something,
			// add the rank manually.

			if ( rankList.isEmpty() )
				rankSelect.addItem( value.getRank() );

			initialRank = value.getRank();
			initialTitle = value.getTitle();
			rankSelect.setSelectedItem( initialRank.toLowerCase() );

			titleField = new JTextField();
			bootCheckBox = new JCheckBox();

			clanKarma = new JLabel( df.format( value.getKarma() ), JLabel.LEFT );

			JButton profileButton = new JButton( JComponentUtilities.getSharedImage( "icon_warning_sml.gif" ) );
			profileButton.addActionListener( new ShowProfileListener() );

			JComponentUtilities.setComponentSize( profileButton, 20, 20 );
			JComponentUtilities.setComponentSize( memberName, 120, 20 );
			JComponentUtilities.setComponentSize( rankSelect, 150, 20 );
			JComponentUtilities.setComponentSize( titleField, 150, 20 );
			JComponentUtilities.setComponentSize( clanKarma, 80, 20 );
			JComponentUtilities.setComponentSize( bootCheckBox, 20, 20 );

			JPanel corePanel = new JPanel();
			corePanel.setLayout( new BoxLayout( corePanel, BoxLayout.X_AXIS ) );
			corePanel.add( Box.createHorizontalStrut( 10 ) );
			corePanel.add( profileButton ); corePanel.add( Box.createHorizontalStrut( 10 ) );
			corePanel.add( memberName ); corePanel.add( Box.createHorizontalStrut( 10 ) );
			corePanel.add( rankSelect ); corePanel.add( Box.createHorizontalStrut( 10 ) );
			corePanel.add( titleField ); corePanel.add( Box.createHorizontalStrut( 10 ) );
			corePanel.add( clanKarma ); corePanel.add( Box.createHorizontalStrut( 10 ) );
			corePanel.add( bootCheckBox ); corePanel.add( Box.createHorizontalStrut( 10 ) );

			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			add( Box.createVerticalStrut( 3 ) );
			add( corePanel );
			add( Box.createVerticalStrut( 2 ) );
		}

		public void updateDisplay( PanelList list, Object value, int index )
		{
			profile = (ProfileRequest) value;
			memberName.setText( profile.getPlayerName() );
			rankSelect.setSelectedItem( profile.getRank() );
			titleField.setText( profile.getTitle() );
			clanKarma.setText( df.format( profile.getKarma() ) );
		}

		private class ShowProfileListener implements ActionListener
		{
			public void actionPerformed( ActionEvent e )
			{
				Object [] parameters = new Object[2];
				parameters[0] = client;
				parameters[1] = profile;

				(new CreateFrameRunnable( ProfileFrame.class, parameters )).run();
			}
		}
	}

	private class SnapshotPanel extends LabeledKoLPanel
	{
		private JCheckBox [] optionBoxes;
		private final String [][] options =
		{
			{ "<td>Lv</td><td>Mus</td><td>Mys</td><td>Mox</td><td>Total</td>", "Progression statistics (level, power, class)" },
			{ "<td>Title</td><td>Rank</td><td>Karma</td>", "Internal clan statistics (title, rank, karma)" },
			{ "<td>Class</td><td>Path</td><td>Turns</td><td>Meat</td>", "Leaderboard statistics (class, path, turns this run, wealth)" },
			{ "<td>PVP</td><td>Food</td><td>Drink</td>", "Miscellaneous statistics (pvp rank, favorite food, favorite booze)" },
			{ "<td>Created</td><td>Last Login</td>", "Creation and last login dates" },
		};

		public SnapshotPanel()
		{
			super( "Clan Snapshot", "snapshot", "logshot", new Dimension( 420, 16 ), new Dimension( 20, 16 ) );

			VerifiableElement [] elements = new VerifiableElement[ options.length ];

			optionBoxes = new JCheckBox[ options.length ];
			for ( int i = 0; i < options.length; ++i )
				optionBoxes[i] = new JCheckBox();

			for ( int i = 0; i < options.length; ++i )
				elements[i] = new VerifiableElement( options[i][1], JLabel.LEFT, optionBoxes[i] );

			setContent( elements, false );
			String tableHeaderSetting = getProperty( "clanRosterHeader" );
			for ( int i = 0; i < options.length; ++i )
				optionBoxes[i].setSelected( tableHeaderSetting.indexOf( options[i][0] ) != -1 );
		}

		protected void actionConfirmed()
		{
			// Apply all the settings before generating the
			// needed clan ClanSnapshotTable.

			StringBuffer tableHeaderSetting = new StringBuffer();

			for ( int i = 0; i < options.length; ++i )
				if ( optionBoxes[i].isSelected() )
					tableHeaderSetting.append( options[i][0] );

			setProperty( "clanRosterHeader", tableHeaderSetting.toString() + "<td>Ascensions</td>" );

			// Now that you've got everything, go ahead and
			// generate the snapshot.

			ClanManager.takeSnapshot( 0, 0, 0, 0, false );
		}

		protected void actionCancelled()
		{
			ClanManager.saveStashLog();
		}
	}

	private class AscensionPanel extends LabeledKoLPanel
	{
		private JTextField mostAscensionsBoardSizeField;
		private JTextField mainBoardSizeField;
		private JTextField classBoardSizeField;
		private JTextField maxAgeField;
		private JCheckBox playerMoreThanOnceOption;

		public AscensionPanel()
		{
			super( "Clan Leaderboards", "snapshot", new Dimension( 240, 20 ), new Dimension( 200, 20 ) );

			mostAscensionsBoardSizeField = new JTextField( "20" );
			mainBoardSizeField = new JTextField( "10" );
			classBoardSizeField = new JTextField( "5" );
			maxAgeField = new JTextField( "0" );
			playerMoreThanOnceOption = new JCheckBox();

			VerifiableElement [] elements = new VerifiableElement[5];
			elements[0] = new VerifiableElement( "Most Ascensions Board Size:  ", mostAscensionsBoardSizeField );
			elements[1] = new VerifiableElement( "Fastest Ascensions Board Size:  ", mainBoardSizeField );
			elements[2] = new VerifiableElement( "Class Breakdown Board Size:  ", classBoardSizeField );
			elements[3] = new VerifiableElement( "Maximum Ascension Age (in days):  ", maxAgeField );
			elements[4] = new VerifiableElement( "Allow Multiple Appearances:  ", playerMoreThanOnceOption );

			setContent( elements );
		}

		protected void actionConfirmed()
		{
			int mostAscensionsBoardSize = mostAscensionsBoardSizeField.getText().equals( "" ) ? Integer.MAX_VALUE : Integer.parseInt( mostAscensionsBoardSizeField.getText() );
			int mainBoardSize = mainBoardSizeField.getText().equals( "" ) ? Integer.MAX_VALUE : Integer.parseInt( mainBoardSizeField.getText() );
			int classBoardSize = classBoardSizeField.getText().equals( "" ) ? Integer.MAX_VALUE : Integer.parseInt( classBoardSizeField.getText() );
			int maxAge = maxAgeField.getText().equals( "" ) ? Integer.MAX_VALUE : Integer.parseInt( maxAgeField.getText() );
			boolean playerMoreThanOnce = playerMoreThanOnceOption.isSelected();

			String oldSetting = getProperty( "clanRosterHeader" );
			setProperty( "clanRosterHeader", "<td>Ascensions</td>" );

			// Now that you've got everything, go ahead and
			// generate the snapshot.

			ClanManager.takeSnapshot( mostAscensionsBoardSize, mainBoardSize, classBoardSize, maxAge, playerMoreThanOnce );
			setProperty( "clanRosterHeader", oldSetting );
		}

		protected void actionCancelled()
		{
			ClanManager.saveStashLog();
		}
	}

	public static void main( String [] args )
	{	(new CreateFrameRunnable( ClanManageFrame.class )).run();
	}
}

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

/**
 * Copyright (c) 2003, Spellcast development team
 * http://spellcast.dev.java.net/
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
 *  [3] Neither the name "Spellcast development team" nor the names of
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
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.awt.BorderLayout;

// event listeners
import javax.swing.SwingUtilities;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

// containers
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;

// spellcast-related imports
import net.java.dev.spellcast.utilities.LockableListModel;

public class AdventureFrame extends KoLFrame
{
	public AdventureFrame( KoLmafia client, LockableListModel list )
	{
		super( "KoLmafia: " + client.getLoginName(), client );
		setResizable( false );

		CardLayout cards = new CardLayout( 10, 10 );
		getContentPane().setLayout( cards );

		contentPanel = new AdventurePanel( list );


		getContentPane().add( contentPanel, "" );

		updateDisplay( LOGGED_IN_STATE, " " );
		setDefaultCloseOperation( DISPOSE_ON_CLOSE );
	}

	protected class AdventurePanel extends KoLPanel
	{
		private JPanel actionStatusPanel;
		private JLabel actionStatusLabel;
		private JLabel serverReplyLabel;

		JComboBox locationField;
		JTextField countField;

		public AdventurePanel( LockableListModel list )
		{
			super( "begin", "cancel" );

			actionStatusPanel = new JPanel();
			actionStatusPanel.setLayout( new GridLayout( 2, 1 ) );

			actionStatusLabel = new JLabel( " ", JLabel.CENTER );
			actionStatusPanel.add( actionStatusLabel );
			serverReplyLabel = new JLabel( " ", JLabel.CENTER );
			actionStatusPanel.add( serverReplyLabel );

			locationField = new JComboBox( list );
			countField = new JTextField();

			VerifiableElement [] elements = new VerifiableElement[2];
			elements[0] = new VerifiableElement( "Location: ", locationField );
			elements[1] = new VerifiableElement( "# of turns: ", countField );

			setContent( elements );
		}

		protected void setContent( VerifiableElement [] elements )
		{
			super.setContent( elements );
			add( actionStatusPanel, BorderLayout.SOUTH );
		}

		public void setStatusMessage( String s )
		{	actionStatusLabel.setText( s );
		}

		public void setEnabled( boolean isEnabled )
		{
			super.setEnabled( isEnabled );
			locationField.setEnabled( isEnabled );
			countField.setEnabled( isEnabled );
		}

		public void clear()
		{
			Runnable updateAComponent = new Runnable() {
				public void run()
				{
					countField.setText( "" );
					requestFocus();
				}
			};
			SwingUtilities.invokeLater(updateAComponent);
		}

		protected void actionConfirmed()
		{
			// Once the stubs are finished, this will notify the
			// client to begin adventuring based on the values
			// placed in the input fields.  For now, since there's
			// no actual functionality, simply parse the values.

			Runnable request = (Runnable) locationField.getSelectedItem();

			try
			{
				int count = Integer.parseInt( countField.getText() );
				updateDisplay( ADVENTURING_STATE, "Request 1 in progress..." );
				client.makeRequest( request, count );
			}
			catch ( NumberFormatException e )
			{
				// If the number placed inside of the count list was not
				// an actual integer value, pretend nothing happened.
				// Using exceptions for flow control is bad style, but
				// this will be fixed once we add functionality.
			}
		}

		protected void actionCancelled()
		{
			// Once the stubs are finished, this will notify the
			// client to terminate the loop early.  For now, since
			// there's no actual functionality, simply request focus

			updateDisplay( LOGGED_IN_STATE, "Adventuring terminated." );
			client.cancelRequest();
			requestFocus();
		}

		public void requestFocus()
		{	locationField.requestFocus();
		}
	}
}
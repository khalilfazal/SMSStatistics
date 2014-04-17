package com.github.capncanuck.smsstatistics;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import com.github.capncanuck.smsstatistics.contact.Contact;
import com.github.capncanuck.smsstatistics.contact.ContactsData;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * The Class MainActivity.
 */
public class MainActivity extends Activity {

    /**
     * The url used to find SMS messages.
     */
    private static final Uri smsUri = Uri.parse("content://sms");

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);

        final TextView content = (TextView) this.findViewById(R.id.content);
        content.setMovementMethod(new ScrollingMovementMethod());

        Query.setAty(this);

        // Initialize the contact list with information from the inbox
        final Set<Contact> contacts = new Query<Set<Contact>>(Uri.withAppendedPath(smsUri, "inbox"), "address") {
            @Override
            protected Set<Contact> ready(final Cursor cursor) {
                final Set<Contact> contacts = new ConcurrentSkipListSet<Contact>();

                while (cursor.moveToNext()) {
                    final PhoneNumber number = new PhoneNumber(cursor.getString(0));
                    final Optional<Contact> maybeContact = Iterables.tryFind(contacts, Contact.checkNumber(number));

                    if (maybeContact.isPresent()) {
                        final Contact contact = maybeContact.get();
                        contacts.remove(contact);
                        contact.incrIncomingCount();
                        contacts.add(contact);
                    } else {
                        contacts.add(new Contact(number, 1, 0));
                    }
                }

                return contacts;
            }
        }.result();

        // Update the contact list with information from the sent box
        new Query<Void>(Uri.withAppendedPath(smsUri, "sent"), "address") {
            @Override
            protected Void ready(final Cursor cursor) {
                while (cursor.moveToNext()) {
                    final PhoneNumber number = new PhoneNumber(cursor.getString(0));
                    final Optional<Contact> maybeContact = Iterables.tryFind(contacts, Contact.checkNumber(number));

                    if (maybeContact.isPresent()) {
                        final Contact contact = maybeContact.get();
                        contacts.remove(contact);
                        contact.incrOutgoingCount();
                        contacts.add(contact);
                    } else {
                        contacts.add(new Contact(number, 0, 1));
                    }
                }

                return null;
            }
        }.result();

        // Update the contact list with display names
        for (final Contact contact : contacts) {
            new Query<Void>(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contact.getRawNumber())),
                    Contacts.DISPLAY_NAME,
                    Contacts.PHOTO_URI) {
                @Override
                protected Void ready(final Cursor cursor) {
                    if (cursor.moveToNext()) {
                        contacts.remove(contact);
                        contact.setName(cursor.getString(0));
                        contact.setPhoto(Optional.fromNullable(cursor.getString(1)));
                        contacts.add(contact);
                    }

                    return null;
                }
            }.result();
        }

        final ContactsData list = new ContactsData(ImmutableList.copyOf(contacts));

        content.setText(list.toString());
    }
}
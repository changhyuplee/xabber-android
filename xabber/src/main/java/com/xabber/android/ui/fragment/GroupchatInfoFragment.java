package com.xabber.android.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.NetworkException;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.ContactJid;
import com.xabber.android.data.extension.groupchat.OnGroupchatRequestListener;
import com.xabber.android.data.extension.groupchat.block.GroupchatBlocklistItemElement;
import com.xabber.android.data.message.chat.AbstractChat;
import com.xabber.android.data.message.chat.ChatManager;
import com.xabber.android.data.message.chat.groupchat.GroupChat;
import com.xabber.android.data.message.chat.groupchat.GroupchatIndexType;
import com.xabber.android.data.message.chat.groupchat.GroupchatManager;
import com.xabber.android.data.message.chat.groupchat.GroupchatMember;
import com.xabber.android.data.message.chat.groupchat.GroupchatMemberManager;
import com.xabber.android.data.message.chat.groupchat.GroupchatMembershipType;
import com.xabber.android.data.message.chat.groupchat.GroupchatPrivacyType;
import com.xabber.android.data.roster.PresenceManager;
import com.xabber.android.ui.activity.GroupchatMemberActivity;
import com.xabber.android.ui.activity.GroupchatSettingsActivity;
import com.xabber.android.ui.activity.GroupchatUpdateSettingsActivity;
import com.xabber.android.ui.adapter.GroupchatMembersAdapter;
import com.xabber.android.utils.StringUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.packet.Presence;

import java.util.ArrayList;
import java.util.Collections;

public class GroupchatInfoFragment extends Fragment implements OnGroupchatRequestListener,
        GroupchatMembersAdapter.OnMemberClickListener {

    private static final String LOG_TAG = GroupchatInfoFragment.class.getSimpleName();
    private static final String ARGUMENT_ACCOUNT = "com.xabber.android.ui.fragment.GroupchatInfoFragment.ARGUMENT_ACCOUNT";
    private static final String ARGUMENT_CONTACT = "com.xabber.android.ui.fragment.GroupchatInfoFragment.ARGUMENT_CONTACT";

    private AccountJid account;
    private ContactJid groupchatContact;
    private AbstractChat groupChat;
    private Presence groupchatPresence;


    // members list
    private ViewGroup membersLayout;
    private GroupchatMembersAdapter membersAdapter;
    private ProgressBar membersProgress;
    private TextView membersHeader;

    private String filterString;
    private AppCompatImageView membersFilterSearchIv;
    private AppCompatImageView membersFilterClearIv;
    private AppCompatEditText membersFilterEt;
    private RelativeLayout membersFilterRootRl;

    // settings layout
    private ViewGroup settingsLayout;
    private TextView invitationsCount;
    private TextView blockedCount;

    // info layout
    private ViewGroup infoLayout;
    private ProgressBar infoProgress;

    private TextView groupchatJidText;

    private ViewGroup groupchatStatusLayout;
    private TextView groupchatStatusText;

    private ViewGroup groupchatNameLayout;
    private TextView groupchatNameText;

    private ViewGroup groupchatDescriptionLayout;
    private TextView groupchatDescriptionText;

    private ViewGroup groupchatIndexLayout;
    private TextView groupchatIndexText;

    private ViewGroup groupchatAnonymityLayout;
    private TextView groupchatAnonymityText;

    private ViewGroup groupchatMembershipLayout;
    private TextView groupchatMembershipText;

    public static GroupchatInfoFragment newInstance(AccountJid account, ContactJid contact) {
        GroupchatInfoFragment fragment = new GroupchatInfoFragment();

        Bundle arguments = new Bundle();
        arguments.putParcelable(ARGUMENT_ACCOUNT, account);
        arguments.putParcelable(ARGUMENT_CONTACT, contact);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Application.getInstance().addUIListener(OnGroupchatRequestListener.class, this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Application.getInstance().removeUIListener(OnGroupchatRequestListener.class, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);
        if (groupChat instanceof GroupChat) {
            updateChatSettings((GroupChat) groupChat);
        }
        requestLists();

        if (groupChat != null && groupChat instanceof GroupChat) {
            try {
                PresenceManager.getInstance().sendPresenceToGroupchat(groupChat, true);
            } catch (NetworkException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);

        if (groupChat != null && groupChat instanceof GroupChat) {
            try {
                PresenceManager.getInstance().sendPresenceToGroupchat(groupChat, false);
            } catch (NetworkException e) {
                e.printStackTrace();
            }
        }
    }



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            account = args.getParcelable(ARGUMENT_ACCOUNT);
            groupchatContact = args.getParcelable(ARGUMENT_CONTACT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groupchat_info, container, false);

        membersLayout = view.findViewById(R.id.membersLayout);
        RecyclerView membersList = view.findViewById(R.id.rvGroupchatMembers);
        membersProgress = view.findViewById(R.id.progressGroupchatMembers);
        membersHeader = view.findViewById(R.id.groupchat_members_header);
        membersList.setNestedScrollingEnabled(false);

        // info layout
        infoLayout = view.findViewById(R.id.infoLayout);
        infoProgress = view.findViewById(R.id.progressInfo);
        groupchatJidText = view.findViewById(R.id.groupchat_jid_name);
        groupchatStatusLayout = view.findViewById(R.id.groupchat_status_layout);
        groupchatStatusText = view.findViewById(R.id.groupchat_status_name);
        groupchatNameLayout = view.findViewById(R.id.groupchat_name_layout);
        groupchatNameText = view.findViewById(R.id.groupchat_name);
        groupchatDescriptionLayout = view.findViewById(R.id.groupchat_description_layout);
        groupchatDescriptionText = view.findViewById(R.id.groupchat_description_name);
        groupchatIndexLayout = view.findViewById(R.id.groupchat_indexed_layout);
        groupchatIndexText = view.findViewById(R.id.groupchat_indexed_name);
        groupchatAnonymityLayout = view.findViewById(R.id.groupchat_anonymity_layout);
        groupchatAnonymityText = view.findViewById(R.id.groupchat_anonymity_name);
        groupchatMembershipLayout = view.findViewById(R.id.groupchat_membership_layout);
        groupchatMembershipText = view.findViewById(R.id.groupchat_membership_name);

        setupFiltering(view);

        settingsLayout = view.findViewById(R.id.settingsLayout);
        view.findViewById(R.id.settingsButtonLayout).setOnClickListener(v ->
                startActivity(GroupchatUpdateSettingsActivity.Companion
                        .createOpenGroupchatSettingsIntentForGroupchat(account, groupchatContact)));

        invitationsCount = view.findViewById(R.id.invitationsCount);
        blockedCount = view.findViewById(R.id.blockedCount);
        //restrictionsButton.setOnClickListener(this);

        view.findViewById(R.id.invitationsButtonLayout).setOnClickListener(v ->
                startActivity(GroupchatSettingsActivity.createIntent(getContext(), account,
                        groupchatContact, GroupchatSettingsActivity.GroupchatSettingsType.Invitations)));

        view.findViewById(R.id.blockedButtonLayout).setOnClickListener(v ->
                startActivity(GroupchatSettingsActivity.createIntent(getContext(), account,
                        groupchatContact, GroupchatSettingsActivity.GroupchatSettingsType.Blocked)));


        membersList.setLayoutManager(new LinearLayoutManager(getActivity(),
                LinearLayoutManager.VERTICAL, false));
        membersAdapter = new GroupchatMembersAdapter(new ArrayList<>(), (GroupChat) ChatManager.getInstance().getChat(account, groupchatContact), this);
        membersList.setAdapter(membersAdapter);

        return view;
    }

    private void setupFiltering(View view){
        membersFilterRootRl = view.findViewById(R.id.search_root);
        membersFilterSearchIv = view.findViewById(R.id.search_iv);
        membersFilterClearIv = view.findViewById(R.id.clear_iv);
        membersFilterEt = view.findViewById(R.id.search_et);

        membersFilterClearIv.setOnClickListener(v -> membersFilterEt.setText(""));

        membersFilterSearchIv.setOnClickListener(v -> membersFilterEt.requestFocus());

        membersFilterEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0){
                    membersFilterClearIv.setVisibility(View.VISIBLE);
                    filterString = s.toString().toLowerCase();
                } else {
                    filterString = "";
                    membersFilterClearIv.setVisibility(View.GONE);
                }
                updateViewsWithMemberList();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    @Override
    public void onMemberClick(GroupchatMember groupchatMember) {
        Intent intent = GroupchatMemberActivity.Companion.createIntentForGroupchatAndMemberId(getActivity(),
                groupchatMember.getId(), (GroupChat) groupChat);
        startActivity(intent);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupchatJidText.setText(groupchatContact.getBareJid());

        groupchatPresence = PresenceManager.getInstance().getPresence(account, groupchatContact);
        groupChat = ChatManager.getInstance().getChat(account, groupchatContact);

        if (groupChat instanceof GroupChat) {
            updateChatInfo((GroupChat) groupChat);
        } else {
            infoProgress.setVisibility(View.VISIBLE);
            infoLayout.setVisibility(View.GONE);
            settingsLayout.setVisibility(View.GONE);
            membersLayout.setVisibility(View.GONE);
        }
    }

    private void requestLists() {
        GroupchatMemberManager.getInstance().requestGroupchatMembers(account, groupchatContact);
        GroupchatMemberManager.getInstance().requestGroupchatInvitationsList(account, groupchatContact);
        GroupchatMemberManager.getInstance().requestGroupchatBlocklistList(account, groupchatContact);
        GroupchatMemberManager.getInstance().requestMe(account, groupchatContact);
        membersProgress.setVisibility(View.VISIBLE);
    }

    private void updateChatInfo(GroupChat groupChat) {
        //GroupchatPresence groupchatPresence = GroupchatManager.getInstance().getGroupchatPresence(groupChat);
        //if (groupchatPresence == null)
        //    return;

        infoLayout.setVisibility(View.VISIBLE);
        infoProgress.setVisibility(View.GONE);

        //GroupchatIndexType indexType = groupchatPresence.getIndex();
        GroupchatIndexType indexType = groupChat.getIndexType();
        if (indexType != null) {
            switch (indexType) {
                case GLOBAL:
                    groupchatIndexText.setText(getString(R.string.groupchat_index_type_global));
                    groupchatIndexLayout.setVisibility(View.VISIBLE);
                    break;
                case LOCAL:
                    groupchatIndexText.setText(getString(R.string.groupchat_index_type_local));
                    groupchatIndexLayout.setVisibility(View.VISIBLE);
                    break;
                default:
                    groupchatIndexLayout.setVisibility(View.GONE);
            }
        } else groupchatIndexLayout.setVisibility(View.GONE);
        GroupchatMembershipType membershipType = groupChat.getMembershipType();
        if (membershipType != null) {
            switch (membershipType) {
                case OPEN:
                    groupchatMembershipText.setText(getString(R.string.groupchat_membership_type_open));
                    groupchatMembershipLayout.setVisibility(View.VISIBLE);
                    break;
                case MEMBER_ONLY:
                    groupchatMembershipText.setText(getString(R.string.groupchat_membership_type_members_only));
                    groupchatMembershipLayout.setVisibility(View.VISIBLE);
                    break;
                case NONE:
                default:
                    groupchatMembershipLayout.setVisibility(View.GONE);
            }
        } else groupchatMembershipLayout.setVisibility(View.GONE);
        GroupchatPrivacyType privacyType = groupChat.getPrivacyType();
        if (privacyType != null) {
            switch (privacyType) {
                case PUBLIC:
                    groupchatAnonymityText.setText(getContext().getString(R.string.groupchat_privacy_type_public));
                    groupchatAnonymityLayout.setVisibility(View.VISIBLE);
                    break;
                case INCOGNITO:
                    groupchatAnonymityText.setText(getString(R.string.groupchat_privacy_type_incognito));
                    groupchatAnonymityLayout.setVisibility(View.VISIBLE);
                    break;
                case NONE:
                default:
                    groupchatAnonymityLayout.setVisibility(View.GONE);
            }
        } else groupchatAnonymityLayout.setVisibility(View.GONE);

        String name = groupChat.getName();
        if (name != null && !name.isEmpty()) {
            groupchatNameText.setText(name);
            groupchatNameLayout.setVisibility(View.VISIBLE);
        } else groupchatNameLayout.setVisibility(View.GONE);

        String description = groupChat.getDescription();
        if (description != null && !description.isEmpty()) {
            groupchatDescriptionText.setText(description);
            groupchatDescriptionLayout.setVisibility(View.VISIBLE);
        } else groupchatDescriptionLayout.setVisibility(View.GONE);

        String status = groupchatPresence.getStatus();
        if (status != null && !status.isEmpty()) {
            groupchatStatusText.setText(status);
            groupchatStatusLayout.setVisibility(View.VISIBLE);
        } else groupchatStatusLayout.setVisibility(View.GONE);

        String presenceStatus = StringUtils.getDisplayStatusForGroupchat(
                groupChat.getNumberOfMembers(),
                groupChat.getNumberOfOnlineMembers(),
                getContext());
        if (presenceStatus != null) membersHeader.setText(presenceStatus);

        settingsLayout.setVisibility(View.VISIBLE);
        updateChatSettings(groupChat);
        membersLayout.setVisibility(View.VISIBLE);
    }

    private void updateChatSettings(GroupChat groupChat) {
        ArrayList<String> listOfInvites = groupChat.getListOfInvites();
        if (listOfInvites != null) {
            if (listOfInvites.isEmpty()) {
                invitationsCount.setText("0");
            } else {
                invitationsCount.setText(String.valueOf(listOfInvites.size()));
            }
        }
        ArrayList<GroupchatBlocklistItemElement> blockList = groupChat.getListOfBlockedElements();
        if (blockList != null) {
            if (blockList.isEmpty()) {
                blockedCount.setText("0");
            } else {
                blockedCount.setText(String.valueOf(blockList.size()));
            }
        }
    }

    private void updateViewsWithMemberList() {
        if (membersAdapter != null) {
            ArrayList<GroupchatMember> list = new ArrayList<>();
            if (filterString != null && !filterString.isEmpty()){
                for (GroupchatMember groupchatMember : GroupchatMemberManager.getInstance().getGroupchatMembers(groupchatContact))
                    if (groupchatMember.getNickname().toLowerCase().contains(filterString)
                        || groupchatMember.getNickname().toLowerCase().contains(StringUtils.translitirateToLatin(filterString))
                        || (groupchatMember.getJid() != null && groupchatMember.getJid().toLowerCase().contains(filterString))
                        || (groupchatMember.getJid() != null && groupchatMember.getJid().toLowerCase().contains(StringUtils.translitirateToLatin(filterString))))
                        list.add(groupchatMember);
            } else list.addAll(GroupchatMemberManager.getInstance()
                    .getGroupchatMembers(groupchatContact));

            Collections.sort(list, (o1, o2) -> {
                if (o1.isMe() && !o2.isMe()) return -1;
                if (o2.isMe() && !o1.isMe()) return 1;
                return 0;
            });

            membersAdapter.setItems(list);
            if (GroupchatMemberManager.checkIfHasActiveMemberListRequest(account, groupchatContact))
                membersProgress.setVisibility(View.VISIBLE);
            else membersProgress.setVisibility(View.GONE);
        }
    }

    @Override
    public void onMeReceived(AccountJid accountJid, ContactJid groupchatJid) {
        if (isThisChat(accountJid, groupchatJid))
            updateViewsWithMemberList();
    }

    @Override
    public void onGroupchatMembersReceived(AccountJid account, ContactJid groupchatJid) {
        if (isThisChat(account, groupchatJid))
            updateViewsWithMemberList();
    }

    @Override
    public void onGroupchatInvitesReceived(AccountJid account, ContactJid groupchatJid) {
        if (isThisChat(account, groupchatJid))
            updateChatSettings((GroupChat) groupChat);
    }

    @Override
    public void onGroupchatBlocklistReceived(AccountJid account, ContactJid groupchatJid) {
        if (isThisChat(account, groupchatJid))
            updateChatSettings((GroupChat) groupChat);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGroupchatPresenceUpdated(GroupchatManager.GroupchatPresenceUpdatedEvent presenceUpdatedEvent) {
        if (isThisChat(presenceUpdatedEvent.getAccount(), presenceUpdatedEvent.getGroupJid()))
            updateChatInfo((GroupChat) groupChat);
    }

    private boolean isThisChat(AccountJid account, ContactJid contactJid) {
        if (account == null) return false;
        if (contactJid == null) return false;
        if (!account.getBareJid().equals(this.account.getBareJid())) return false;
        return contactJid.getBareJid().equals(this.groupchatContact.getBareJid());
    }

    public interface GroupchatSelectorListItemActions {
        void onListItemSelected();
        void onListItemDeselected();
    }
}

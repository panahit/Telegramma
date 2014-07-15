/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.text.Html;

import org.telegram.messenger.BuffersStorage;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageKeyData;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLClassStore;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.objects.PhotoObject;
import org.telegram.ui.ApplicationLoader;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class MessagesController implements NotificationCenter.NotificationCenterDelegate {
    public ConcurrentHashMap<Integer, TLRPC.Chat> chats = new ConcurrentHashMap<Integer, TLRPC.Chat>(100, 1.0f, 2);
    public ConcurrentHashMap<Integer, TLRPC.EncryptedChat> encryptedChats = new ConcurrentHashMap<Integer, TLRPC.EncryptedChat>(10, 1.0f, 2);
    public ConcurrentHashMap<Integer, TLRPC.User> users = new ConcurrentHashMap<Integer, TLRPC.User>(100, 1.0f, 2);
    public ArrayList<TLRPC.TL_dialog> dialogs = new ArrayList<TLRPC.TL_dialog>();
    public ArrayList<TLRPC.TL_dialog> dialogsServerOnly = new ArrayList<TLRPC.TL_dialog>();
    public ConcurrentHashMap<Long, TLRPC.TL_dialog> dialogs_dict = new ConcurrentHashMap<Long, TLRPC.TL_dialog>(100, 1.0f, 2);
    public HashMap<Integer, MessageObject> dialogMessage = new HashMap<Integer, MessageObject>();
    public ConcurrentHashMap<Long, ArrayList<PrintingUser>> printingUsers = new ConcurrentHashMap<Long, ArrayList<PrintingUser>>(100, 1.0f, 2);
    public HashMap<Long, CharSequence> printingStrings = new HashMap<Long, CharSequence>();
    private int lastPrintingStringCount = 0;

    private HashMap<String, ArrayList<DelayedMessage>> delayedMessages = new HashMap<String, ArrayList<DelayedMessage>>();
    public HashMap<Integer, MessageObject> sendingMessages = new HashMap<Integer, MessageObject>();
    public HashMap<Integer, TLRPC.User> hidenAddToContacts = new HashMap<Integer, TLRPC.User>();
    private HashMap<Integer, TLRPC.EncryptedChat> acceptingChats = new HashMap<Integer, TLRPC.EncryptedChat>();
    private ArrayList<TLRPC.Updates> updatesQueue = new ArrayList<TLRPC.Updates>();
    private ArrayList<Long> pendingEncMessagesToDelete = new ArrayList<Long>();
    private long updatesStartWaitTime = 0;
    public ArrayList<TLRPC.Update> delayedEncryptedChatUpdates = new ArrayList<TLRPC.Update>();
    private boolean startingSecretChat = false;

    private boolean gettingNewDeleteTask = false;
    private int currentDeletingTaskTime = 0;
    private Long currentDeletingTask = null;
    private ArrayList<Integer> currentDeletingTaskMids = null;

    public int totalDialogsCount = 0;
    public boolean loadingDialogs = false;
    public boolean dialogsEndReached = false;
    public boolean gettingDifference = false;
    public boolean gettingDifferenceAgain = false;
    public boolean updatingState = false;
    public boolean firstGettingTask = false;
    public boolean registeringForPush = false;

    private long lastStatusUpdateTime = 0;
    private long statusRequest = 0;
    private int statusSettingState = 0;
    private boolean offlineSent = false;
    private String uploadingAvatar = null;

    public boolean enableJoined = true;
    public int fontSize = AndroidUtilities.dp(16);

    private class UserActionUpdates extends TLRPC.Updates {

    }

    public static final int MESSAGE_SEND_STATE_SENDING = 1;
    public static final int MESSAGE_SEND_STATE_SENT = 0;
    public static final int MESSAGE_SEND_STATE_SEND_ERROR = 2;

    public static final int UPDATE_MASK_NAME = 1;
    public static final int UPDATE_MASK_AVATAR = 2;
    public static final int UPDATE_MASK_STATUS = 4;
    public static final int UPDATE_MASK_CHAT_AVATAR = 8;
    public static final int UPDATE_MASK_CHAT_NAME = 16;
    public static final int UPDATE_MASK_CHAT_MEMBERS = 32;
    public static final int UPDATE_MASK_USER_PRINT = 64;
    public static final int UPDATE_MASK_USER_PHONE = 128;
    public static final int UPDATE_MASK_READ_DIALOG_MESSAGE = 256;
    public static final int UPDATE_MASK_ALL = UPDATE_MASK_AVATAR | UPDATE_MASK_STATUS | UPDATE_MASK_NAME | UPDATE_MASK_CHAT_AVATAR | UPDATE_MASK_CHAT_NAME | UPDATE_MASK_CHAT_MEMBERS | UPDATE_MASK_USER_PRINT | UPDATE_MASK_USER_PHONE | UPDATE_MASK_READ_DIALOG_MESSAGE;

    public static class PrintingUser {
        public long lastTime;
        public int userId;
    }

    private class DelayedMessage {
        public TLRPC.TL_messages_sendMedia sendRequest;
        public TLRPC.TL_decryptedMessage sendEncryptedRequest;
        public int type;
        public String originalPath;
        public TLRPC.FileLocation location;
        public TLRPC.TL_video videoLocation;
        public TLRPC.TL_audio audioLocation;
        public TLRPC.TL_document documentLocation;
        public MessageObject obj;
        public TLRPC.EncryptedChat encryptedChat;
    }

    public static final int didReceivedNewMessages = 1;
    public static final int updateInterfaces = 3;
    public static final int dialogsNeedReload = 4;
    public static final int closeChats = 5;
    public static final int messagesDeleted = 6;
    public static final int messagesReaded = 7;
    public static final int messagesDidLoaded = 8;

    public static final int messageReceivedByAck = 9;
    public static final int messageReceivedByServer = 10;
    public static final int messageSendError = 11;

    public static final int reloadSearchResults = 12;

    public static final int contactsDidLoaded = 13;

    public static final int chatDidCreated = 15;
    public static final int chatDidFailCreate = 16;

    public static final int chatInfoDidLoaded = 17;

    public static final int mediaDidLoaded = 18;
    public static final int mediaCountDidLoaded = 20;

    public static final int encryptedChatUpdated = 21;
    public static final int messagesReadedEncrypted = 22;
    public static final int encryptedChatCreated = 23;

    public static final int userPhotosLoaded = 24;

    public static final int removeAllMessagesFromDialog = 25;

    public static final int notificationsSettingsUpdated = 26;

    private static volatile MessagesController Instance = null;
    public static MessagesController getInstance() {
        MessagesController localInstance = Instance;
        if (localInstance == null) {
            synchronized (MessagesController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new MessagesController();
                }
            }
        }
        return localInstance;
    }

    public MessagesController() {
        MessagesStorage storage = MessagesStorage.getInstance();
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidUpload);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidFailUpload);
        NotificationCenter.getInstance().addObserver(this, 10);
        addSupportUser();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        enableJoined = preferences.getBoolean("EnableContactJoined", true);
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        fontSize = preferences.getInt("fons_size", 16);
    }

    public void addSupportUser() {
        TLRPC.TL_userForeign user = new TLRPC.TL_userForeign();
        user.phone = "333";
        user.id = 333000;
        user.first_name = "Telegram";
        user.last_name = "";
        user.status = null;
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        users.put(user.id, user);
    }

    public static TLRPC.InputUser getInputUser(TLRPC.User user) {
        if (user == null) {
            return null;
        }
        TLRPC.InputUser inputUser = null;
        if (user.id == UserConfig.getClientUserId()) {
            inputUser = new TLRPC.TL_inputUserSelf();
        } else if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
            inputUser = new TLRPC.TL_inputUserForeign();
            inputUser.user_id = user.id;
            inputUser.access_hash = user.access_hash;
        } else {
            inputUser = new TLRPC.TL_inputUserContact();
            inputUser.user_id = user.id;
        }
        return inputUser;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == FileLoader.FileDidUpload) {
            final String location = (String)args[0];
            final TLRPC.InputFile file = (TLRPC.InputFile)args[1];
            final TLRPC.InputEncryptedFile encryptedFile = (TLRPC.InputEncryptedFile)args[2];

            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                req.caption = "";
                req.crop = new TLRPC.TL_inputPhotoCropAuto();
                req.file = file;
                req.geo_point = new TLRPC.TL_inputGeoPointEmpty();
                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            TLRPC.User user = users.get(UserConfig.getClientUserId());
                            if (user == null) {
                                user = UserConfig.getCurrentUser();
                                users.put(user.id, user);
                            } else {
                                UserConfig.setCurrentUser(user);
                            }
                            if (user == null) {
                                return;
                            }
                            TLRPC.TL_photos_photo photo = (TLRPC.TL_photos_photo) response;
                            ArrayList<TLRPC.PhotoSize> sizes = photo.photo.sizes;
                            TLRPC.PhotoSize smallSize = PhotoObject.getClosestPhotoSizeWithSize(sizes, 100, 100);
                            TLRPC.PhotoSize bigSize = PhotoObject.getClosestPhotoSizeWithSize(sizes, 1000, 1000);
                            user.photo = new TLRPC.TL_userProfilePhoto();
                            user.photo.photo_id = photo.photo.id;
                            if (smallSize != null) {
                                user.photo.photo_small = smallSize.location;
                            }
                            if (bigSize != null) {
                                user.photo.photo_big = bigSize.location;
                            } else if (smallSize != null) {
                                user.photo.photo_small = smallSize.location;
                            }
                            MessagesStorage.getInstance().clearUserPhotos(user.id);
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                            users.add(user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationCenter.getInstance().postNotificationName(updateInterfaces, UPDATE_MASK_AVATAR);
                                    UserConfig.saveConfig(true);
                                }
                            });
                        }
                    }
                });
            } else {
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        ArrayList<DelayedMessage> arr = delayedMessages.get(location);
                        if (arr != null) {
                            for (int a = 0; a < arr.size(); a++) {
                                DelayedMessage message = arr.get(a);
                                if (file != null && message.sendRequest != null) {
                                    if (message.type == 0) {
                                        message.sendRequest.media.file = file;
                                        performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                                    } else if (message.type == 1) {
                                        if (message.sendRequest.media.thumb == null) {
                                            message.sendRequest.media.thumb = file;
                                            performSendDelayedMessage(message);
                                        } else {
                                            message.sendRequest.media.file = file;
                                            performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                                        }
                                    } else if (message.type == 2) {
                                        if (message.sendRequest.media.thumb == null && message.location != null) {
                                            message.sendRequest.media.thumb = file;
                                            performSendDelayedMessage(message);
                                        } else {
                                            message.sendRequest.media.file = file;
                                            performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                                        }
                                    } else if (message.type == 3) {
                                        message.sendRequest.media.file = file;
                                        performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                                    }
                                    arr.remove(a);
                                    a--;
                                } else if (encryptedFile != null && message.sendEncryptedRequest != null) {
                                    message.sendEncryptedRequest.media.key = encryptedFile.key;
                                    message.sendEncryptedRequest.media.iv = encryptedFile.iv;
                                    performSendEncryptedRequest(message.sendEncryptedRequest, message.obj, message.encryptedChat, encryptedFile, message.originalPath);
                                    arr.remove(a);
                                    a--;
                                }
                            }
                            if (arr.isEmpty()) {
                                delayedMessages.remove(location);
                            }
                        }
                    }
                });
            }
        } else if (id == FileLoader.FileDidFailUpload) {
            final String location = (String) args[0];
            final boolean enc = (Boolean) args[1];

            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                uploadingAvatar = null;
            } else {
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        ArrayList<DelayedMessage> arr = delayedMessages.get(location);
                        if (arr != null) {
                            for (int a = 0; a < arr.size(); a++) {
                                DelayedMessage obj = arr.get(a);
                                if (enc && obj.sendEncryptedRequest != null || !enc && obj.sendRequest != null) {
                                    obj.obj.messageOwner.send_state = MESSAGE_SEND_STATE_SEND_ERROR;
                                    sendingMessages.remove(obj.obj.messageOwner.id);
                                    arr.remove(a);
                                    a--;
                                    NotificationCenter.getInstance().postNotificationName(messageSendError, obj.obj.messageOwner.id);
                                }
                            }
                            if (arr.isEmpty()) {
                                delayedMessages.remove(location);
                            }
                        }
                    }
                });
            }
        } else if (id == messageReceivedByServer) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = dialogMessage.get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer)args[1];
                dialogMessage.remove(msgId);
                dialogMessage.put(newMsgId, obj);
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SENT;

                long uid;
                if (obj.messageOwner.to_id.chat_id != 0) {
                    uid = -obj.messageOwner.to_id.chat_id;
                } else {
                    if (obj.messageOwner.to_id.user_id == UserConfig.getClientUserId()) {
                        obj.messageOwner.to_id.user_id = obj.messageOwner.from_id;
                    }
                    uid = obj.messageOwner.to_id.user_id;
                }

                TLRPC.TL_dialog dialog = dialogs_dict.get(uid);
                if (dialog != null) {
                    if (dialog.top_message == msgId) {
                        dialog.top_message = newMsgId;
                    }
                }
                NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileDidUpload);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileDidFailUpload);
        NotificationCenter.getInstance().removeObserver(this, messageReceivedByServer);
    }

    public void cleanUp() {
        ContactsController.getInstance().cleanup();
        MediaController.getInstance().cleanup();
        NotificationsController.getInstance().cleanup();

        dialogs_dict.clear();
        dialogs.clear();
        dialogsServerOnly.clear();
        acceptingChats.clear();
        users.clear();
        chats.clear();
        sendingMessages.clear();
        delayedMessages.clear();
        dialogMessage.clear();
        printingUsers.clear();
        printingStrings.clear();
        totalDialogsCount = 0;
        lastPrintingStringCount = 0;
        hidenAddToContacts.clear();
        updatesQueue.clear();
        pendingEncMessagesToDelete.clear();
        delayedEncryptedChatUpdates.clear();

        updatesStartWaitTime = 0;
        currentDeletingTaskTime = 0;
        currentDeletingTaskMids = null;
        gettingNewDeleteTask = false;
        currentDeletingTask = null;
        loadingDialogs = false;
        dialogsEndReached = false;
        gettingDifference = false;
        gettingDifferenceAgain = false;
        firstGettingTask = false;
        updatingState = false;
        lastStatusUpdateTime = 0;
        offlineSent = false;
        registeringForPush = false;
        uploadingAvatar = null;
        startingSecretChat = false;
        statusRequest = 0;
        statusSettingState = 0;
        addSupportUser();
    }

    public void didAddedNewTask(final int minDate) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (currentDeletingTask == null && !gettingNewDeleteTask || currentDeletingTaskTime != 0 && minDate < currentDeletingTaskTime) {
                    getNewDeleteTask(null);
                }
            }
        });
    }

    public void getNewDeleteTask(final Long oldTask) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                gettingNewDeleteTask = true;
                MessagesStorage.getInstance().getNewTask(oldTask);
            }
        });
    }

    private void checkDeletingTask() {
        int currentServerTime = ConnectionsManager.getInstance().getCurrentTime();

        if (currentDeletingTask != null && currentDeletingTaskTime != 0 && currentDeletingTaskTime <= currentServerTime) {
            currentDeletingTaskTime = 0;
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    deleteMessages(currentDeletingTaskMids, null, null);

                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            getNewDeleteTask(currentDeletingTask);
                            currentDeletingTaskTime = 0;
                            currentDeletingTask = null;
                        }
                    });
                }
            });
        }
    }

    public void processLoadedDeleteTask(final Long taskId, final int taskTime, final ArrayList<Integer> messages) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                gettingNewDeleteTask = false;
                if (taskId != null) {
                    currentDeletingTaskTime = taskTime;
                    currentDeletingTask = taskId;
                    currentDeletingTaskMids = messages;

                    checkDeletingTask();
                } else {
                    currentDeletingTaskTime = 0;
                    currentDeletingTask = null;
                    currentDeletingTaskMids = null;
                }
            }
        });
    }

    public void loadUserPhotos(final int uid, final int offset, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (fromCache) {
            MessagesStorage.getInstance().getUserPhotos(uid, offset, count, max_id, classGuid);
        } else {
            TLRPC.User user = users.get(uid);
            if (user == null) {
                return;
            }
            TLRPC.TL_photos_getUserPhotos req = new TLRPC.TL_photos_getUserPhotos();
            req.limit = count;
            req.offset = offset;
            req.max_id = (int)max_id;
            req.user_id = getInputUser(user);
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.photos_Photos res = (TLRPC.photos_Photos) response;
                        processLoadedUserPhotos(res, uid, offset, count, max_id, fromCache, classGuid);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void processLoadedUserPhotos(final TLRPC.photos_Photos res, final int uid, final int offset, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (!fromCache) {
            MessagesStorage.getInstance().putUsersAndChats(res.users, null, true, true);
            MessagesStorage.getInstance().putUserPhotos(uid, res);
        } else if (res == null || res.photos.isEmpty()) {
            loadUserPhotos(uid, offset, count, max_id, false, classGuid);
            return;
        }
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                for (TLRPC.User user : res.users) {
                    if (fromCache) {
                        users.putIfAbsent(user.id, user);
                    } else {
                        users.put(user.id, user);
                        if (user.id == UserConfig.getClientUserId()) {
                            UserConfig.setCurrentUser(user);
                        }
                    }
                }
                NotificationCenter.getInstance().postNotificationName(userPhotosLoaded, uid, offset, count, fromCache, classGuid, res.photos);
            }
        });
    }

    public void processLoadedMedia(final TLRPC.messages_Messages res, final long uid, int offset, int count, int max_id, final boolean fromCache, final int classGuid) {
        int lower_part = (int)uid;
        if (fromCache && res.messages.isEmpty() && lower_part != 0) {
            loadMedia(uid, offset, count, max_id, false, classGuid);
        } else {
            if (!fromCache) {
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                MessagesStorage.getInstance().putMedia(uid, res.messages);
            }

            final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
            for (TLRPC.User u : res.users) {
                usersLocal.put(u.id, u);
            }
            final ArrayList<MessageObject> objects = new ArrayList<MessageObject>();
            for (TLRPC.Message message : res.messages) {
                objects.add(new MessageObject(message, usersLocal));
            }

            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    int totalCount;
                    if (res instanceof TLRPC.TL_messages_messagesSlice) {
                        totalCount = res.count;
                    } else {
                        totalCount = res.messages.size();
                    }
                    for (TLRPC.User user : res.users) {
                        if (fromCache) {
                            users.putIfAbsent(user.id, user);
                        } else {
                            users.put(user.id, user);
                            if (user.id == UserConfig.getClientUserId()) {
                                UserConfig.setCurrentUser(user);
                            }
                        }
                    }
                    for (TLRPC.Chat chat : res.chats) {
                        if (fromCache) {
                            chats.putIfAbsent(chat.id, chat);
                        } else {
                            chats.put(chat.id, chat);
                        }
                    }
                    NotificationCenter.getInstance().postNotificationName(mediaDidLoaded, uid, totalCount, objects, fromCache, classGuid);
                }
            });
        }
    }

    public void loadMedia(final long uid, final int offset, final int count, final int max_id, final boolean fromCache, final int classGuid) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            MessagesStorage.getInstance().loadMedia(uid, offset, count, max_id, classGuid);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.offset = offset;
            req.limit = count;
            req.max_id = max_id;
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            req.q = "";
            if (uid < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = users.get(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                }
                req.peer.user_id = lower_part;
            }
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        processLoadedMedia(res, uid, offset, count, max_id, false, classGuid);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void processLoadedMediaCount(final int count, final long uid, final int classGuid, final boolean fromCache) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                int lower_part = (int)uid;
                if (fromCache && count == -1 && lower_part != 0) {
                    getMediaCount(uid, classGuid, false);
                } else {
                    if (!fromCache) {
                        MessagesStorage.getInstance().putMediaCount(uid, count);
                    }
                    if (fromCache && count == -1) {
                        NotificationCenter.getInstance().postNotificationName(mediaCountDidLoaded, uid, 0, fromCache);
                    } else {
                        NotificationCenter.getInstance().postNotificationName(mediaCountDidLoaded, uid, count, fromCache);
                    }
                }
            }
        });
    }

    public void getMediaCount(final long uid, final int classGuid, boolean fromCache) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            MessagesStorage.getInstance().getMediaCount(uid, classGuid);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.offset = 0;
            req.limit = 1;
            req.max_id = 0;
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            req.q = "";
            if (uid < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = users.get(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                }
                req.peer.user_id = lower_part;
            }
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        if (res instanceof TLRPC.TL_messages_messagesSlice) {
                            processLoadedMediaCount(res.count, uid, classGuid, false);
                        } else {
                            processLoadedMediaCount(res.messages.size(), uid, classGuid, false);
                        }
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void uploadAndApplyUserAvatar(TLRPC.PhotoSize bigPhoto) {
        if (bigPhoto != null) {
            uploadingAvatar = AndroidUtilities.getCacheDir() + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
            FileLoader.getInstance().uploadFile(uploadingAvatar, false);
        }
    }

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat) {
        for (Integer id : messages) {
            MessageObject obj = dialogMessage.get(id);
            if (obj != null) {
                obj.deleted = true;
            }
        }
        MessagesStorage.getInstance().markMessagesAsDeleted(messages, true);
        MessagesStorage.getInstance().updateDialogsWithDeletedMessages(messages, true);
        NotificationCenter.getInstance().postNotificationName(messagesDeleted, messages);

        if (randoms != null && encryptedChat != null && !randoms.isEmpty()) {
            sendMessagesDeleteMessage(randoms, encryptedChat);
        }

        ArrayList<Integer> toSend = new ArrayList<Integer>();
        for (Integer mid : messages) {
            if (mid > 0) {
                toSend.add(mid);
            }
        }
        if (toSend.isEmpty()) {
            return;
        }
        TLRPC.TL_messages_deleteMessages req = new TLRPC.TL_messages_deleteMessages();
        req.id = messages;
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public void deleteDialog(final long did, int offset, final boolean onlyHistory) {
        TLRPC.TL_dialog dialog = dialogs_dict.get(did);
        if (dialog != null) {
            int lower_part = (int)did;

            if (offset == 0) {
                if (!onlyHistory) {
                    dialogs.remove(dialog);
                    dialogsServerOnly.remove(dialog);
                    dialogs_dict.remove(did);
                    totalDialogsCount--;
                }
                dialogMessage.remove(dialog.top_message);
                MessagesStorage.getInstance().deleteDialog(did, onlyHistory);
                NotificationCenter.getInstance().postNotificationName(removeAllMessagesFromDialog, did);
                NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
            }

            if (lower_part != 0) {
                TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
                req.offset = offset;
                if (did < 0) {
                    req.peer = new TLRPC.TL_inputPeerChat();
                    req.peer.chat_id = -lower_part;
                } else {
                    TLRPC.User user = users.get(lower_part);
                    if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                        req.peer = new TLRPC.TL_inputPeerForeign();
                        req.peer.access_hash = user.access_hash;
                    } else {
                        req.peer = new TLRPC.TL_inputPeerContact();
                    }
                    req.peer.user_id = lower_part;
                }
                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                            if (res.offset > 0) {
                                deleteDialog(did, res.offset, onlyHistory);
                            }
                            if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                                MessagesStorage.lastSeqValue = res.seq;
                                MessagesStorage.lastPtsValue = res.pts;
                                MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                            } else if (MessagesStorage.lastSeqValue != res.seq) {
                                FileLog.e("tmessages", "need get diff TL_messages_deleteHistory, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                                if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                                    if (updatesStartWaitTime == 0) {
                                        updatesStartWaitTime = System.currentTimeMillis();
                                    }
                                    FileLog.e("tmessages", "add TL_messages_deleteHistory to queue");
                                    UserActionUpdates updates = new UserActionUpdates();
                                    updates.seq = res.seq;
                                    updatesQueue.add(updates);
                                } else {
                                    getDifference();
                                }
                            }
                        }
                    }
                });
            } else {
                int encId = (int)(did >> 32);
                if (onlyHistory) {
                    TLRPC.EncryptedChat encryptedChat = encryptedChats.get(encId);
                    sendClearHistoryMessage(encryptedChat);
                } else {
                    declineSecretChat(encId);
                }
            }
        }
    }

    public void loadChatInfo(final int chat_id) {
        MessagesStorage.getInstance().loadChatInfo(chat_id);
    }

    public void processChatInfo(final int chat_id, final TLRPC.ChatParticipants info, final ArrayList<TLRPC.User> usersArr, final boolean fromCache) {
        if (info == null && fromCache) {
            TLRPC.TL_messages_getFullChat req = new TLRPC.TL_messages_getFullChat();
            req.chat_id = chat_id;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error != null) {
                        return;
                    }
                    final TLRPC.TL_messages_chatFull res = (TLRPC.TL_messages_chatFull) response;
                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                    MessagesStorage.getInstance().updateChatInfo(chat_id, res.full_chat.participants, false);
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            for (TLRPC.User user : res.users) {
                                users.put(user.id, user);
                                if (user.id == UserConfig.getClientUserId()) {
                                    UserConfig.setCurrentUser(user);
                                }
                            }
                            for (TLRPC.Chat chat : res.chats) {
                                chats.put(chat.id, chat);
                            }
                            NotificationCenter.getInstance().postNotificationName(chatInfoDidLoaded, chat_id, res.full_chat.participants);
                        }
                    });
                }
            });
        } else {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    for (TLRPC.User user : usersArr) {
                        if (fromCache) {
                            users.putIfAbsent(user.id, user);
                        } else {
                            users.put(user.id, user);
                            if (user.id == UserConfig.getClientUserId()) {
                                UserConfig.setCurrentUser(user);
                            }
                        }
                    }
                    NotificationCenter.getInstance().postNotificationName(chatInfoDidLoaded, chat_id, info);
                }
            });
        }
    }

    public void updateTimerProc() {
        long currentTime = System.currentTimeMillis();

        checkDeletingTask();

        if (UserConfig.isClientActivated()) {
            if (ConnectionsManager.getInstance().getPauseTime() == 0 && ApplicationLoader.isScreenOn) {
                if (statusSettingState != 1 && (lastStatusUpdateTime == 0 || lastStatusUpdateTime <= System.currentTimeMillis() - 55000 || offlineSent)) {
                    statusSettingState = 1;

                    if (statusRequest != 0) {
                        ConnectionsManager.getInstance().cancelRpc(statusRequest, true);
                    }

                    TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                    req.offline = false;
                    statusRequest = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            if (error == null) {
                                lastStatusUpdateTime = System.currentTimeMillis();
                                offlineSent = false;
                                statusSettingState = 0;
                            } else {
                                if (lastStatusUpdateTime != 0) {
                                    lastStatusUpdateTime += 5000;
                                }
                            }
                            statusRequest = 0;
                        }
                    });
                }
            } else if (statusSettingState != 2 && !offlineSent && ConnectionsManager.getInstance().getPauseTime() <= System.currentTimeMillis() - 2000) {
                statusSettingState = 2;
                if (statusRequest != 0) {
                    ConnectionsManager.getInstance().cancelRpc(statusRequest, true);
                }
                TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                req.offline = true;
                statusRequest = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            offlineSent = true;
                        } else {
                            if (lastStatusUpdateTime != 0) {
                                lastStatusUpdateTime += 5000;
                            }
                        }
                        statusRequest = 0;
                    }
                });
            }

            if (updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 < currentTime) {
                FileLog.e("tmessages", "UPDATES WAIT TIMEOUT - CHECK QUEUE");
                processUpdatesQueue(false);
            }
        }
        if (!printingUsers.isEmpty() || lastPrintingStringCount != printingUsers.size()) {
            boolean updated = false;
            ArrayList<Long> keys = new ArrayList<Long>(printingUsers.keySet());
            for (int b = 0; b < keys.size(); b++) {
                Long key = keys.get(b);
                ArrayList<PrintingUser> arr = printingUsers.get(key);
                for (int a = 0; a < arr.size(); a++) {
                    PrintingUser user = arr.get(a);
                    if (user.lastTime + 5900 < currentTime) {
                        updated = true;
                        arr.remove(user);
                        a--;
                    }
                }
                if (arr.isEmpty()) {
                    printingUsers.remove(key);
                    keys.remove(b);
                    b--;
                }
            }

            updatePrintingStrings();

            if (updated) {
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(updateInterfaces, UPDATE_MASK_USER_PRINT);
                    }
                });
            }
        }
    }

    public void updatePrintingStrings() {
        final HashMap<Long, CharSequence> newPrintingStrings = new HashMap<Long, CharSequence>();

        ArrayList<Long> keys = new ArrayList<Long>(printingUsers.keySet());
        for (Long key : keys) {
            if (key > 0 || key.intValue() == 0) {
                newPrintingStrings.put(key, LocaleController.getString("Typing", R.string.Typing));
            } else {
                ArrayList<PrintingUser> arr = printingUsers.get(key);
                int count = 0;
                String label = "";
                for (PrintingUser pu : arr) {
                    TLRPC.User user = users.get(pu.userId);
                    if (user != null) {
                        if (label.length() != 0) {
                            label += ", ";
                        }
                        label += Utilities.formatName(user.first_name, user.last_name);
                        count++;
                    }
                    if (count == 2) {
                        break;
                    }
                }
                if (label.length() != 0) {
                    if (count > 1) {
                        if (arr.size() > 2) {
                            newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s", label, LocaleController.formatPluralString("AndMoreTyping", arr.size() - 2))));
                        } else {
                            newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s", label, LocaleController.getString("AreTyping", R.string.AreTyping))));
                        }
                    } else {
                        newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s", label, LocaleController.getString("IsTyping", R.string.IsTyping))));
                    }
                }
            }
        }

        lastPrintingStringCount = newPrintingStrings.size();

        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                printingStrings = newPrintingStrings;
            }
        });
    }

    public void sendTyping(long dialog_id, int classGuid) {
        if (dialog_id == 0) {
            return;
        }
        int lower_part = (int)dialog_id;
        if (lower_part != 0) {
            TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = users.get(lower_part);
                if (user != null) {
                    if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                        req.peer = new TLRPC.TL_inputPeerForeign();
                        req.peer.user_id = user.id;
                        req.peer.access_hash = user.access_hash;
                    } else {
                        req.peer = new TLRPC.TL_inputPeerContact();
                        req.peer.user_id = user.id;
                    }
                } else {
                    return;
                }
            }
            req.typing = true;
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        } else {
            int encId = (int)(dialog_id >> 32);
            TLRPC.EncryptedChat chat = encryptedChats.get(encId);
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_setEncryptedTyping req = new TLRPC.TL_messages_setEncryptedTyping();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.typing = true;
                long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
                ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public void loadMessages(final long dialog_id, final int offset, final int count, final int max_id, boolean fromCache, int midDate, final int classGuid, boolean from_unread, boolean forward) {
        int lower_part = (int)dialog_id;
        if (fromCache || lower_part == 0) {
            MessagesStorage.getInstance().getMessages(dialog_id, offset, count, max_id, midDate, classGuid, from_unread, forward);
        } else {
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = users.get(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.user_id = user.id;
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                    req.peer.user_id = user.id;
                }
            }
            req.offset = offset;
            req.limit = count;
            req.max_id = max_id;
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        processLoadedMessages(res, dialog_id, offset, count, max_id, false, classGuid, 0, 0, 0, 0, false);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void processLoadedMessages(final TLRPC.messages_Messages messagesRes, final long dialog_id, final int offset, final int count, final int max_id, final boolean isCache, final int classGuid, final int first_unread, final int last_unread, final int unread_count, final int last_date, final boolean isForward) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                int lower_id = (int)dialog_id;
                if (!isCache) {
                    MessagesStorage.getInstance().putMessages(messagesRes, dialog_id);
                }
                if (lower_id != 0 && isCache && messagesRes.messages.size() == 0 && !isForward) {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadMessages(dialog_id, offset, count, max_id, false, 0, classGuid, false, false);
                        }
                    });
                    return;
                }
                final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
                for (TLRPC.User u : messagesRes.users) {
                    usersLocal.put(u.id, u);
                }
                final ArrayList<MessageObject> objects = new ArrayList<MessageObject>();
                for (TLRPC.Message message : messagesRes.messages) {
                    message.dialog_id = dialog_id;
                    objects.add(new MessageObject(message, usersLocal));
                }
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User u : messagesRes.users) {
                            if (isCache) {
                                if (u.id == UserConfig.getClientUserId() || u.id / 1000 == 333) {
                                    users.put(u.id, u);
                                } else {
                                    users.putIfAbsent(u.id, u);
                                }
                            } else {
                                users.put(u.id, u);
                                if (u.id == UserConfig.getClientUserId()) {
                                    UserConfig.setCurrentUser(u);
                                }
                            }
                        }
                        for (TLRPC.Chat c : messagesRes.chats) {
                            if (isCache) {
                                chats.putIfAbsent(c.id, c);
                            } else {
                                chats.put(c.id, c);
                            }
                        }
                        NotificationCenter.getInstance().postNotificationName(messagesDidLoaded, dialog_id, offset, count, objects, isCache, first_unread, last_unread, unread_count, last_date, isForward);
                    }
                });
            }
        });
    }

    public void loadDialogs(final int offset, final int serverOffset, final int count, boolean fromCache) {
        if (loadingDialogs) {
            return;
        }
        loadingDialogs = true;

        if (fromCache) {
            MessagesStorage.getInstance().getDialogs(offset, serverOffset, count);
        } else {
            TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
            req.offset = serverOffset;
            req.limit = count;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
                        processLoadedDialogs(dialogsRes, null, offset, serverOffset, count, false, false);
                    }
                }
            });
        }
    }

    private void applyDialogsNotificationsSettings(ArrayList<TLRPC.TL_dialog> dialogs) {
        SharedPreferences.Editor editor = null;
        for (TLRPC.TL_dialog dialog : dialogs) {
            if (dialog.peer != null && dialog.notify_settings instanceof TLRPC.TL_peerNotifySettings) {
                if (editor == null) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    editor = preferences.edit();
                }
                int dialog_id = dialog.peer.user_id;
                if (dialog_id == 0) {
                    dialog_id = -dialog.peer.chat_id;
                }
                if (dialog.notify_settings.mute_until != 0) {
                    editor.putInt("notify2_" + dialog_id, 2);
                }
            }
        }
        if (editor != null) {
            editor.commit();
        }
    }

    public void processDialogsUpdateRead(final HashMap<Long, Integer> dialogsToUpdate) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                for (HashMap.Entry<Long, Integer> entry : dialogsToUpdate.entrySet()) {
                    TLRPC.TL_dialog currentDialog = dialogs_dict.get(entry.getKey());
                    if (currentDialog != null) {
                        currentDialog.unread_count = entry.getValue();
                    }
                }
                NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate, true);
                NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
            }
        });
    }

    public void processDialogsUpdate(final TLRPC.messages_Dialogs dialogsRes, ArrayList<TLRPC.EncryptedChat> encChats) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final HashMap<Long, TLRPC.TL_dialog> new_dialogs_dict = new HashMap<Long, TLRPC.TL_dialog>();
                final HashMap<Integer, MessageObject> new_dialogMessage = new HashMap<Integer, MessageObject>();
                final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
                final HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();

                for (TLRPC.User u : dialogsRes.users) {
                    usersLocal.put(u.id, u);
                }

                for (TLRPC.Message m : dialogsRes.messages) {
                    new_dialogMessage.put(m.id, new MessageObject(m, usersLocal));
                }
                for (TLRPC.TL_dialog d : dialogsRes.dialogs) {
                    if (d.last_message_date == 0) {
                        MessageObject mess = new_dialogMessage.get(d.top_message);
                        if (mess != null) {
                            d.last_message_date = mess.messageOwner.date;
                        }
                    }
                    if (d.id == 0) {
                        if (d.peer instanceof TLRPC.TL_peerUser) {
                            d.id = d.peer.user_id;
                        } else if (d.peer instanceof TLRPC.TL_peerChat) {
                            d.id = -d.peer.chat_id;
                        }
                    }
                    new_dialogs_dict.put(d.id, d);
                    dialogsToUpdate.put(d.id, d.unread_count);
                }

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User u : dialogsRes.users) {
                            users.putIfAbsent(u.id, u);
                        }
                        for (TLRPC.Chat c : dialogsRes.chats) {
                            chats.putIfAbsent(c.id, c);
                        }

                        for (HashMap.Entry<Long, TLRPC.TL_dialog> pair : new_dialogs_dict.entrySet()) {
                            long key = pair.getKey();
                            TLRPC.TL_dialog value = pair.getValue();
                            TLRPC.TL_dialog currentDialog = dialogs_dict.get(key);
                            if (currentDialog == null) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                            } else {
                                currentDialog.unread_count = value.unread_count;
                                MessageObject oldMsg = dialogMessage.get(currentDialog.top_message);
                                if (oldMsg == null || currentDialog.top_message > 0) {
                                    if (oldMsg != null && oldMsg.deleted || value.top_message > currentDialog.top_message) {
                                        dialogs_dict.put(key, value);
                                        if (oldMsg != null) {
                                            dialogMessage.remove(oldMsg.messageOwner.id);
                                        }
                                        dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                                    }
                                } else {
                                    MessageObject newMsg = new_dialogMessage.get(value.top_message);
                                    if (oldMsg.deleted || newMsg == null || newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                        dialogs_dict.put(key, value);
                                        dialogMessage.remove(oldMsg.messageOwner.id);
                                        dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                                    }
                                }
                            }
                        }

                        dialogs.clear();
                        dialogsServerOnly.clear();
                        dialogs.addAll(dialogs_dict.values());
                        Collections.sort(dialogs, new Comparator<TLRPC.TL_dialog>() {
                            @Override
                            public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                                if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                    return 0;
                                } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            }
                        });
                        for (TLRPC.TL_dialog d : dialogs) {
                            if ((int)d.id != 0) {
                                dialogsServerOnly.add(d);
                            }
                        }
                        NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate, true);
                        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                    }
                });
             }
        });
    }

    public void processLoadedDialogs(final TLRPC.messages_Dialogs dialogsRes, final ArrayList<TLRPC.EncryptedChat> encChats, final int offset, final int serverOffset, final int count, final boolean isCache, final boolean resetEnd) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (isCache && dialogsRes.dialogs.size() == 0) {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            for (TLRPC.User u : dialogsRes.users) {
                                if (isCache) {
                                    if (u.id == UserConfig.getClientUserId() || u.id / 1000 == 333) {
                                        users.put(u.id, u);
                                    } else {
                                        users.putIfAbsent(u.id, u);
                                    }
                                } else {
                                    users.put(u.id, u);
                                    if (u.id == UserConfig.getClientUserId()) {
                                        UserConfig.setCurrentUser(u);
                                    }
                                }
                            }
                            loadingDialogs = false;
                            if (resetEnd) {
                                dialogsEndReached = false;
                            }
                            loadDialogs(offset, serverOffset, count, false);
                            NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                        }
                    });
                    return;
                }
                final HashMap<Long, TLRPC.TL_dialog> new_dialogs_dict = new HashMap<Long, TLRPC.TL_dialog>();
                final HashMap<Integer, MessageObject> new_dialogMessage = new HashMap<Integer, MessageObject>();
                final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
                int new_totalDialogsCount;

                if (!isCache) {
                    MessagesStorage.getInstance().putDialogs(dialogsRes);
                }

                if (dialogsRes instanceof TLRPC.TL_messages_dialogsSlice) {
                    TLRPC.TL_messages_dialogsSlice slice = (TLRPC.TL_messages_dialogsSlice)dialogsRes;
                    new_totalDialogsCount = slice.count;
                } else {
                    new_totalDialogsCount = dialogsRes.dialogs.size();
                }

                for (TLRPC.User u : dialogsRes.users) {
                    usersLocal.put(u.id, u);
                }

                for (TLRPC.Message m : dialogsRes.messages) {
                    new_dialogMessage.put(m.id, new MessageObject(m, usersLocal));
                }
                for (TLRPC.TL_dialog d : dialogsRes.dialogs) {
                    if (d.last_message_date == 0) {
                        MessageObject mess = new_dialogMessage.get(d.top_message);
                        if (mess != null) {
                            d.last_message_date = mess.messageOwner.date;
                        }
                    }
                    if (d.id == 0) {
                        if (d.peer instanceof TLRPC.TL_peerUser) {
                            d.id = d.peer.user_id;
                        } else if (d.peer instanceof TLRPC.TL_peerChat) {
                            d.id = -d.peer.chat_id;
                        }
                    }
                    new_dialogs_dict.put(d.id, d);
                }

                final int arg1 = new_totalDialogsCount;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCache) {
                            applyDialogsNotificationsSettings(dialogsRes.dialogs);
                        }
                        for (TLRPC.User u : dialogsRes.users) {
                            if (isCache) {
                                if (u.id == UserConfig.getClientUserId() || u.id / 1000 == 333) {
                                    users.put(u.id, u);
                                } else {
                                    users.putIfAbsent(u.id, u);
                                }
                            } else {
                                users.put(u.id, u);
                                if (u.id == UserConfig.getClientUserId()) {
                                    UserConfig.setCurrentUser(u);
                                }
                            }
                        }
                        for (TLRPC.Chat c : dialogsRes.chats) {
                            if (isCache) {
                                chats.putIfAbsent(c.id, c);
                            } else {
                                chats.put(c.id, c);
                            }
                        }
                        if (encChats != null) {
                            for (TLRPC.EncryptedChat encryptedChat : encChats) {
                                encryptedChats.put(encryptedChat.id, encryptedChat);
                            }
                        }
                        loadingDialogs = false;
                        totalDialogsCount = arg1;

                        for (HashMap.Entry<Long, TLRPC.TL_dialog> pair : new_dialogs_dict.entrySet()) {
                            long key = pair.getKey();
                            TLRPC.TL_dialog value = pair.getValue();
                            TLRPC.TL_dialog currentDialog = dialogs_dict.get(key);
                            if (currentDialog == null) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                            } else {
                                MessageObject oldMsg = dialogMessage.get(value.top_message);
                                if (oldMsg == null || currentDialog.top_message > 0) {
                                    if (oldMsg != null && oldMsg.deleted || value.top_message > currentDialog.top_message) {
                                        if (oldMsg != null) {
                                            dialogMessage.remove(oldMsg.messageOwner.id);
                                        }
                                        dialogs_dict.put(key, value);
                                        dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                                    }
                                } else {
                                    MessageObject newMsg = new_dialogMessage.get(value.top_message);
                                    if (oldMsg.deleted || newMsg == null || newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                        dialogMessage.remove(oldMsg.messageOwner.id);
                                        dialogs_dict.put(key, value);
                                        dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                                    }
                                }
                            }
                        }

                        dialogs.clear();
                        dialogsServerOnly.clear();
                        dialogs.addAll(dialogs_dict.values());
                        Collections.sort(dialogs, new Comparator<TLRPC.TL_dialog>() {
                            @Override
                            public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                                if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                    return 0;
                                } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            }
                        });
                        for (TLRPC.TL_dialog d : dialogs) {
                            if ((int)d.id != 0) {
                                dialogsServerOnly.add(d);
                            }
                        }

                        dialogsEndReached = (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && !isCache;
                        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                    }
                });
            }
        });
    }

    public TLRPC.TL_photo generatePhotoSizes(String path, Uri imageUri) {
        long time = System.currentTimeMillis();
        Bitmap bitmap = FileLoader.loadBitmap(path, imageUri, 800, 800);
        ArrayList<TLRPC.PhotoSize> sizes = new ArrayList<TLRPC.PhotoSize>();
        TLRPC.PhotoSize size = FileLoader.scaleAndSaveImage(bitmap, 90, 90, 55, true);
        if (size != null) {
            size.type = "s";
            sizes.add(size);
        }
        size = FileLoader.scaleAndSaveImage(bitmap, 320, 320, 80, false);
        if (size != null) {
            size.type = "m";
            sizes.add(size);
        }
        size = FileLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false);
        if (size != null) {
            size.type = "x";
            sizes.add(size);
        }
        if (bitmap != null) {
            bitmap.recycle();
        }
        if (sizes.isEmpty()) {
            return null;
        } else {
            UserConfig.saveConfig(false);
            TLRPC.TL_photo photo = new TLRPC.TL_photo();
            photo.user_id = UserConfig.getClientUserId();
            photo.date = ConnectionsManager.getInstance().getCurrentTime();
            photo.sizes = sizes;
            photo.caption = "";
            photo.geo = new TLRPC.TL_geoPointEmpty();
            return photo;
        }
    }

    public void markDialogAsRead(final long dialog_id, final int max_id, final int max_positive_id, final int offset, final int max_date, final boolean was) {
        int lower_part = (int)dialog_id;
        if (lower_part != 0) {
            if (max_id == 0 && offset == 0) {
                return;
            }
            TLRPC.TL_messages_readHistory req = new TLRPC.TL_messages_readHistory();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = users.get(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.user_id = user.id;
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                    req.peer.user_id = user.id;
                }
            }
            req.max_id = max_positive_id;
            req.offset = offset;
            if (offset == 0) {
                NotificationsController.getInstance().processReadMessages(null, dialog_id, 0, max_positive_id);
                MessagesStorage.getInstance().processPendingRead(dialog_id, max_positive_id, max_date, false);
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
                                if (dialog != null) {
                                    dialog.unread_count = 0;
                                    NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                                }
                                HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();
                                dialogsToUpdate.put(dialog_id, 0);
                                NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate, true);
                            }
                        });
                    }
                });
            }
            if (req.max_id != Integer.MAX_VALUE) {
                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            MessagesStorage.getInstance().processPendingRead(dialog_id, max_positive_id, max_date, true);
                            TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                            if (res.offset > 0) {
                                markDialogAsRead(dialog_id, 0, max_positive_id, res.offset, max_date, was);
                            }

                            if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                                MessagesStorage.lastSeqValue = res.seq;
                                MessagesStorage.lastPtsValue = res.pts;
                                MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                            } else if (MessagesStorage.lastSeqValue != res.seq) {
                                FileLog.e("tmessages", "need get diff TL_messages_readHistory, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                                if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                                    if (updatesStartWaitTime == 0) {
                                        updatesStartWaitTime = System.currentTimeMillis();
                                    }
                                    FileLog.e("tmessages", "add TL_messages_readHistory to queue");
                                    UserActionUpdates updates = new UserActionUpdates();
                                    updates.seq = res.seq;
                                    updatesQueue.add(updates);
                                } else {
                                    getDifference();
                                }
                            }
                        }
                    }
                });
            }

            if (offset == 0) {
                TLRPC.TL_messages_receivedMessages req2 = new TLRPC.TL_messages_receivedMessages();
                req2.max_id = max_positive_id;
                ConnectionsManager.getInstance().performRpc(req2, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
            }
        } else {
            if (max_date == 0) {
                return;
            }
            NotificationsController.getInstance().processReadMessages(null, dialog_id, max_date, 0);
            int encId = (int)(dialog_id >> 32);
            TLRPC.EncryptedChat chat = encryptedChats.get(encId);
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_readEncryptedHistory req = new TLRPC.TL_messages_readEncryptedHistory();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.max_date = max_date;

                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        //MessagesStorage.getInstance().processPendingRead(dialog_id, max_id, max_date, true);
                    }
                });
            }
            MessagesStorage.getInstance().processPendingRead(dialog_id, max_id, max_date, false);

            MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
                            if (dialog != null) {
                                dialog.unread_count = 0;
                                NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                            }
                            HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();
                            dialogsToUpdate.put(dialog_id, 0);
                            NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate, true);
                        }
                    });
                }
            });

            if (chat.ttl > 0 && was) {
                int serverTime = Math.max(ConnectionsManager.getInstance().getCurrentTime(), max_date);
                MessagesStorage.getInstance().createTaskForDate(chat.id, serverTime, serverTime, 0);
            }
        }
    }

    public void cancelSendingMessage(MessageObject object) {
        String keyToRemvoe = null;
        boolean enc = false;
        for (HashMap.Entry<String, ArrayList<DelayedMessage>> entry : delayedMessages.entrySet()) {
            ArrayList<DelayedMessage> messages = entry.getValue();
            for (int a = 0; a < messages.size(); a++) {
                DelayedMessage message = messages.get(a);
                if (message.obj.messageOwner.id == object.messageOwner.id) {
                    messages.remove(a);
                    if (messages.size() == 0) {
                        keyToRemvoe = entry.getKey();
                        if (message.sendEncryptedRequest != null) {
                            enc = true;
                        }
                    }
                    break;
                }
            }
        }
        if (keyToRemvoe != null) {
            FileLoader.getInstance().cancelUploadFile(keyToRemvoe, enc);
        }
        ArrayList<Integer> messages = new ArrayList<Integer>();
        messages.add(object.messageOwner.id);
        deleteMessages(messages, null, null);
    }

    private long getNextRandomId() {
        long val = 0;
        while (val == 0) {
            val = Utilities.random.nextLong();
        }
        return val;
    }

    public void sendMessage(TLRPC.User user, long peer) {
        sendMessage(null, 0, 0, null, null, null, null, user, null, null, null, peer);
    }

    public void sendMessage(MessageObject message, long peer) {
        sendMessage(null, 0, 0, null, null, message, null, null, null, null, null, peer);
    }

    public void sendMessage(TLRPC.TL_document document, String originalPath, long peer) {
        sendMessage(null, 0, 0, null, null, null, null, null, document, null, originalPath, peer);
    }

    public void sendMessage(String message, long peer) {
        sendMessage(message, 0, 0, null, null, null, null, null, null, null, null, peer);
    }

    public void sendMessage(TLRPC.FileLocation location, long peer) {
        sendMessage(null, 0, 0, null, null, null, location, null, null, null, null, peer);
    }

    public void sendMessage(double lat, double lon, long peer) {
        sendMessage(null, lat, lon, null, null, null, null, null, null, null, null, peer);
    }

    public void sendMessage(TLRPC.TL_photo photo, String originalPath, long peer) {
        sendMessage(null, 0, 0, photo, null, null, null, null, null, null, originalPath, peer);
    }

    public void sendMessage(TLRPC.TL_video video, String originalPath, long peer) {
        sendMessage(null, 0, 0, null, video, null, null, null, null, null, originalPath, peer);
    }

    public void sendMessage(TLRPC.TL_audio audio, long peer) {
        sendMessage(null, 0, 0, null, null, null, null, null, null, audio, null, peer);
    }

    private void processPendingEncMessages() {
        if (pendingEncMessagesToDelete.isEmpty()) {
            return;
        }
        ArrayList<Long> arr = new ArrayList<Long>(pendingEncMessagesToDelete);
        MessagesStorage.getInstance().markMessagesAsDeletedByRandoms(arr);
        pendingEncMessagesToDelete.clear();
    }

    private void sendMessagesDeleteMessage(ArrayList<Long> random_ids, TLRPC.EncryptedChat encryptedChat) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        reqSend.random_id = getNextRandomId();
        reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(Utilities.random.nextDouble() * 16))];
        Utilities.random.nextBytes(reqSend.random_bytes);
        reqSend.action = new TLRPC.TL_decryptedMessageActionDeleteMessages();
        reqSend.action.random_ids = random_ids;
        performSendEncryptedRequest(reqSend, null, encryptedChat, null, null);

    }

    private void sendClearHistoryMessage(TLRPC.EncryptedChat encryptedChat) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        reqSend.random_id = getNextRandomId();
        reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(Utilities.random.nextDouble() * 16))];
        Utilities.random.nextBytes(reqSend.random_bytes);
        reqSend.action = new TLRPC.TL_decryptedMessageActionFlushHistory();
        performSendEncryptedRequest(reqSend, null, encryptedChat, null, null);
    }

    public void sendTTLMessage(TLRPC.EncryptedChat encryptedChat) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();

        newMsg.action = new TLRPC.TL_messageActionTTLChange();
        newMsg.action.ttl = encryptedChat.ttl;
        newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
        newMsg.from_id = UserConfig.getClientUserId();
        newMsg.unread = true;
        newMsg.dialog_id = ((long)encryptedChat.id) << 32;
        newMsg.to_id = new TLRPC.TL_peerUser();
        if (encryptedChat.participant_id == UserConfig.getClientUserId()) {
            newMsg.to_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.to_id.user_id = encryptedChat.participant_id;
        }
        newMsg.out = true;
        newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
        newMsg.random_id = getNextRandomId();
        UserConfig.saveConfig(false);
        final MessageObject newMsgObj = new MessageObject(newMsg, users);
        newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENDING;

        final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
        objArr.add(newMsgObj);
        ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
        arr.add(newMsg);
        MessagesStorage.getInstance().putMessages(arr, false, true);
        updateInterfaceWithMessages(newMsg.dialog_id, objArr);
        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);

        sendingMessages.put(newMsg.id, newMsgObj);

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        reqSend.random_id = newMsg.random_id;
        reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(Utilities.random.nextDouble() * 16))];
        Utilities.random.nextBytes(reqSend.random_bytes);
        reqSend.action = new TLRPC.TL_decryptedMessageActionSetMessageTTL();
        reqSend.action.ttl_seconds = encryptedChat.ttl;
        performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null, null);
    }

    public void sendScreenshotMessage(TLRPC.EncryptedChat encryptedChat, ArrayList<Long> random_ids) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageActionScreenshotMessages action = new TLRPC.TL_decryptedMessageActionScreenshotMessages();
        action.random_ids = random_ids;

        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();

        newMsg.action = new TLRPC.TL_messageEcryptedAction();
        newMsg.action.encryptedAction = action;

        newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
        newMsg.from_id = UserConfig.getClientUserId();
        newMsg.unread = true;
        newMsg.dialog_id = ((long)encryptedChat.id) << 32;
        newMsg.to_id = new TLRPC.TL_peerUser();
        if (encryptedChat.participant_id == UserConfig.getClientUserId()) {
            newMsg.to_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.to_id.user_id = encryptedChat.participant_id;
        }
        newMsg.out = true;
        newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
        newMsg.random_id = getNextRandomId();
        UserConfig.saveConfig(false);
        final MessageObject newMsgObj = new MessageObject(newMsg, users);
        newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENDING;

        final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
        objArr.add(newMsgObj);
        ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
        arr.add(newMsg);
        MessagesStorage.getInstance().putMessages(arr, false, true);
        updateInterfaceWithMessages(newMsg.dialog_id, objArr);
        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);

        sendingMessages.put(newMsg.id, newMsgObj);

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        reqSend.random_id = newMsg.random_id;
        reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(Utilities.random.nextDouble() * 16))];
        Utilities.random.nextBytes(reqSend.random_bytes);
        reqSend.action = action;
        performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null, null);
    }

    private void sendMessage(String message, double lat, double lon, TLRPC.TL_photo photo, TLRPC.TL_video video, MessageObject msgObj, TLRPC.FileLocation location, TLRPC.User user, TLRPC.TL_document document, TLRPC.TL_audio audio, String originalPath, long peer) {
        TLRPC.Message newMsg = null;
        int type = -1;
        if (message != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaEmpty();
            type = 0;
            newMsg.message = message;
        } else if (lat != 0 && lon != 0) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaGeo();
            newMsg.media.geo = new TLRPC.TL_geoPoint();
            newMsg.media.geo.lat = lat;
            newMsg.media.geo._long = lon;
            newMsg.message = "";
            type = 1;
        } else if (photo != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaPhoto();
            newMsg.media.photo = photo;
            type = 2;
            newMsg.message = "-1";
            TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
            newMsg.attachPath = AndroidUtilities.getCacheDir() + "/" + location1.volume_id + "_" + location1.local_id + ".jpg";
        } else if (video != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaVideo();
            newMsg.media.video = video;
            type = 3;
            newMsg.message = "-1";
            newMsg.attachPath = video.path;
        } else if (msgObj != null) {
            newMsg = new TLRPC.TL_messageForwarded();
            if (msgObj.messageOwner instanceof TLRPC.TL_messageForwarded) {
                newMsg.fwd_from_id = msgObj.messageOwner.fwd_from_id;
                newMsg.fwd_date = msgObj.messageOwner.fwd_date;
                newMsg.media = msgObj.messageOwner.media;
                newMsg.message = msgObj.messageOwner.message;
                newMsg.fwd_msg_id = msgObj.messageOwner.id;
                newMsg.attachPath = msgObj.messageOwner.attachPath;
                type = 4;
            } else {
                newMsg.fwd_from_id = msgObj.messageOwner.from_id;
                newMsg.fwd_date = msgObj.messageOwner.date;
                newMsg.media = msgObj.messageOwner.media;
                newMsg.message = msgObj.messageOwner.message;
                newMsg.fwd_msg_id = msgObj.messageOwner.id;
                newMsg.attachPath = msgObj.messageOwner.attachPath;
                type = 4;
            }
        } else if (location != null) {

        } else if (user != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaContact();
            newMsg.media.phone_number = user.phone;
            newMsg.media.first_name = user.first_name;
            newMsg.media.last_name = user.last_name;
            newMsg.media.user_id = user.id;
            newMsg.message = "";
            type = 6;
        } else if (document != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaDocument();
            newMsg.media.document = document;
            type = 7;
            newMsg.message = "-1";
            newMsg.attachPath = document.path;
        } else if (audio != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaAudio();
            newMsg.media.audio = audio;
            type = 8;
            newMsg.message = "-1";
            newMsg.attachPath = audio.path;
        }
        if (newMsg == null) {
            return;
        }
        newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
        newMsg.from_id = UserConfig.getClientUserId();
        newMsg.unread = true;
        newMsg.dialog_id = peer;
        int lower_id = (int)peer;
        TLRPC.EncryptedChat encryptedChat = null;
        TLRPC.InputPeer sendToPeer = null;
        if (lower_id != 0) {
            if (lower_id < 0) {
                newMsg.to_id = new TLRPC.TL_peerChat();
                newMsg.to_id.chat_id = -lower_id;
                sendToPeer = new TLRPC.TL_inputPeerChat();
                sendToPeer.chat_id = -lower_id;
            } else {
                newMsg.to_id = new TLRPC.TL_peerUser();
                newMsg.to_id.user_id = lower_id;

                TLRPC.User sendToUser = users.get(lower_id);
                if (sendToUser == null) {
                    return;
                }
                if (sendToUser instanceof TLRPC.TL_userForeign || sendToUser instanceof TLRPC.TL_userRequest) {
                    sendToPeer = new TLRPC.TL_inputPeerForeign();
                    sendToPeer.user_id = sendToUser.id;
                    sendToPeer.access_hash = sendToUser.access_hash;
                } else {
                    sendToPeer = new TLRPC.TL_inputPeerContact();
                    sendToPeer.user_id = sendToUser.id;
                }
            }
        } else {
            encryptedChat = encryptedChats.get((int)(peer >> 32));
            newMsg.to_id = new TLRPC.TL_peerUser();
            if (encryptedChat.participant_id == UserConfig.getClientUserId()) {
                newMsg.to_id.user_id = encryptedChat.admin_id;
            } else {
                newMsg.to_id.user_id = encryptedChat.participant_id;
            }
            newMsg.ttl = encryptedChat.ttl;
        }
        newMsg.out = true;
        newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
        newMsg.random_id = getNextRandomId();
        UserConfig.saveConfig(false);
        final MessageObject newMsgObj = new MessageObject(newMsg, null);
        newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENDING;

        final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
        objArr.add(newMsgObj);
        ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
        arr.add(newMsg);
        MessagesStorage.getInstance().putMessages(arr, false, true);
        updateInterfaceWithMessages(peer, objArr);
        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);

        sendingMessages.put(newMsg.id, newMsgObj);

        if (type == 0) {
            if (encryptedChat == null) {
                TLRPC.TL_messages_sendMessage reqSend = new TLRPC.TL_messages_sendMessage();
                reqSend.message = message;
                reqSend.peer = sendToPeer;
                reqSend.random_id = newMsg.random_id;
                performSendMessageRequest(reqSend, newMsgObj, null);
            } else {
                TLRPC.TL_decryptedMessage reqSend = new TLRPC.TL_decryptedMessage();
                reqSend.random_id = newMsg.random_id;
                reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(Utilities.random.nextDouble() * 16))];
                Utilities.random.nextBytes(reqSend.random_bytes);
                reqSend.message = message;
                reqSend.media = new TLRPC.TL_decryptedMessageMediaEmpty();
                performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null, null);
            }
        } else if (type >= 1 && type <= 3 || type >= 5 && type <= 8) {
            if (encryptedChat == null) {
                TLRPC.TL_messages_sendMedia reqSend = new TLRPC.TL_messages_sendMedia();
                reqSend.peer = sendToPeer;
                reqSend.random_id = newMsg.random_id;
                if (type == 1) {
                    reqSend.media = new TLRPC.TL_inputMediaGeoPoint();
                    reqSend.media.geo_point = new TLRPC.TL_inputGeoPoint();
                    reqSend.media.geo_point.lat = lat;
                    reqSend.media.geo_point._long = lon;
                    performSendMessageRequest(reqSend, newMsgObj, null);
                } else if (type == 2) {
                    if (photo.access_hash == 0) {
                        reqSend.media = new TLRPC.TL_inputMediaUploadedPhoto();
                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.sendRequest = reqSend;
                        delayedMessage.type = 0;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto();
                        media.id = new TLRPC.TL_inputPhoto();
                        media.id.id = photo.id;
                        media.id.access_hash = photo.access_hash;
                        reqSend.media = media;
                        performSendMessageRequest(reqSend, newMsgObj, null);
                    }
                } else if (type == 3) {
                    if (video.access_hash == 0) {
                        reqSend.media = new TLRPC.TL_inputMediaUploadedThumbVideo();
                        reqSend.media.duration = video.duration;
                        reqSend.media.w = video.w;
                        reqSend.media.h = video.h;
                        reqSend.media.mime_type = video.mime_type;
                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.sendRequest = reqSend;
                        delayedMessage.type = 1;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.location = video.thumb.location;
                        delayedMessage.videoLocation = video;
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        TLRPC.TL_inputMediaVideo media = new TLRPC.TL_inputMediaVideo();
                        media.id = new TLRPC.TL_inputVideo();
                        media.id.id = video.id;
                        media.id.access_hash = video.access_hash;
                        reqSend.media = media;
                        performSendMessageRequest(reqSend, newMsgObj, null);
                    }
                } else if (type == 6) {
                    reqSend.media = new TLRPC.TL_inputMediaContact();
                    reqSend.media.phone_number = user.phone;
                    reqSend.media.first_name = user.first_name;
                    reqSend.media.last_name = user.last_name;
                    performSendMessageRequest(reqSend, newMsgObj, null);
                } else if (type == 7) {
                    if (document.access_hash == 0) {
                        if (document.thumb.location != null && document.thumb.location instanceof TLRPC.TL_fileLocation) {
                            reqSend.media = new TLRPC.TL_inputMediaUploadedThumbDocument();
                        } else {
                            reqSend.media = new TLRPC.TL_inputMediaUploadedDocument();
                        }
                        reqSend.media.mime_type = document.mime_type;
                        reqSend.media.file_name = document.file_name;
                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.sendRequest = reqSend;
                        delayedMessage.type = 2;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.documentLocation = document;
                        delayedMessage.location = document.thumb.location;
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                        media.id = new TLRPC.TL_inputDocument();
                        media.id.id = document.id;
                        media.id.access_hash = document.access_hash;
                        reqSend.media = media;
                        performSendMessageRequest(reqSend, newMsgObj, null);
                    }
                } else if (type == 8) {
                    if (audio.access_hash == 0) {
                        reqSend.media = new TLRPC.TL_inputMediaUploadedAudio();
                        reqSend.media.duration = audio.duration;
                        reqSend.media.mime_type = audio.mime_type;
                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.sendRequest = reqSend;
                        delayedMessage.type = 3;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.audioLocation = audio;
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        TLRPC.TL_inputMediaAudio media = new TLRPC.TL_inputMediaAudio();
                        media.id = new TLRPC.TL_inputAudio();
                        media.id.id = audio.id;
                        media.id.access_hash = audio.access_hash;
                        reqSend.media = media;
                        performSendMessageRequest(reqSend, newMsgObj, null);
                    }
                }
            } else {
                TLRPC.TL_decryptedMessage reqSend = new TLRPC.TL_decryptedMessage();
                reqSend.random_id = newMsg.random_id;
                reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(Utilities.random.nextDouble() * 16))];
                Utilities.random.nextBytes(reqSend.random_bytes);
                reqSend.message = "";
                if (type == 1) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaGeoPoint();
                    reqSend.media.lat = lat;
                    reqSend.media._long = lon;
                    performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null, null);
                } else if (type == 2) {
                    TLRPC.PhotoSize small = photo.sizes.get(0);
                    TLRPC.PhotoSize big = photo.sizes.get(photo.sizes.size() - 1);
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaPhoto();
                    reqSend.media.thumb = small.bytes;
                    reqSend.media.thumb_h = small.h;
                    reqSend.media.thumb_w = small.w;
                    reqSend.media.w = big.w;
                    reqSend.media.h = big.h;
                    reqSend.media.size = big.size;
                    if (big.location.key == null) {
                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.sendEncryptedRequest = reqSend;
                        delayedMessage.type = 0;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.encryptedChat = encryptedChat;
                        delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                        encryptedFile.id = big.location.volume_id;
                        encryptedFile.access_hash = big.location.secret;
                        reqSend.media.key = big.location.key;
                        reqSend.media.iv = big.location.iv;
                        performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, encryptedFile, null);
                    }
                } else if (type == 3) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo_old();
                    reqSend.media.duration = video.duration;
                    reqSend.media.size = video.size;
                    reqSend.media.w = video.w;
                    reqSend.media.h = video.h;
                    reqSend.media.thumb = video.thumb.bytes;
                    reqSend.media.thumb_h = video.thumb.h;
                    reqSend.media.thumb_w = video.thumb.w;
                    reqSend.media.mime_type = "video/mp4";
                    if (video.access_hash == 0) {
                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.sendEncryptedRequest = reqSend;
                        delayedMessage.type = 1;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.encryptedChat = encryptedChat;
                        delayedMessage.videoLocation = video;
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                        encryptedFile.id = video.id;
                        encryptedFile.access_hash = video.access_hash;
                        reqSend.media.key = video.key;
                        reqSend.media.iv = video.iv;
                        performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, encryptedFile, null);
                    }
                } else if (type == 6) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaContact();
                    reqSend.media.phone_number = user.phone;
                    reqSend.media.first_name = user.first_name;
                    reqSend.media.last_name = user.last_name;
                    reqSend.media.user_id = user.id;
                    performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null, null);
                } else if (type == 7) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                    reqSend.media.size = document.size;
                    if (!(document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                        reqSend.media.thumb = document.thumb.bytes;
                        reqSend.media.thumb_h = document.thumb.h;
                        reqSend.media.thumb_w = document.thumb.w;
                    } else {
                        reqSend.media.thumb = new byte[0];
                        reqSend.media.thumb_h = 0;
                        reqSend.media.thumb_w = 0;
                    }
                    reqSend.media.file_name = document.file_name;
                    reqSend.media.mime_type = document.mime_type;
                    if (document.access_hash == 0) {
                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.sendEncryptedRequest = reqSend;
                        delayedMessage.type = 2;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.encryptedChat = encryptedChat;
                        delayedMessage.documentLocation = document;
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                        encryptedFile.id = document.id;
                        encryptedFile.access_hash = document.access_hash;
                        reqSend.media.key = document.key;
                        reqSend.media.iv = document.iv;
                        performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, encryptedFile, null);
                    }
                } else if (type == 8) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaAudio_old();
                    reqSend.media.duration = audio.duration;
                    reqSend.media.size = audio.size;
                    reqSend.media.mime_type = "audio/ogg";

                    DelayedMessage delayedMessage = new DelayedMessage();
                    delayedMessage.sendEncryptedRequest = reqSend;
                    delayedMessage.type = 3;
                    delayedMessage.obj = newMsgObj;
                    delayedMessage.encryptedChat = encryptedChat;
                    delayedMessage.audioLocation = audio;
                    performSendDelayedMessage(delayedMessage);
                }
            }
        } else if (type == 4) {
            TLRPC.TL_messages_forwardMessage reqSend = new TLRPC.TL_messages_forwardMessage();
            reqSend.peer = sendToPeer;
            reqSend.random_id = newMsg.random_id;
            if (msgObj.messageOwner.id >= 0) {
                reqSend.id = msgObj.messageOwner.id;
            } else {
                reqSend.id = msgObj.messageOwner.fwd_msg_id;
            }
            performSendMessageRequest(reqSend, newMsgObj, null);
        }
    }

    private void processSentMessage(TLRPC.Message newMsg, TLRPC.Message sentMessage, TLRPC.EncryptedFile file, TLRPC.DecryptedMessage decryptedMessage, String originalPath) {
        if (sentMessage != null) {
            if (sentMessage.media instanceof TLRPC.TL_messageMediaPhoto && sentMessage.media.photo != null && newMsg.media instanceof TLRPC.TL_messageMediaPhoto && newMsg.media.photo != null) {
                MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.photo, 0);

                for (TLRPC.PhotoSize size : sentMessage.media.photo.sizes) {
                    if (size instanceof TLRPC.TL_photoSizeEmpty) {
                        continue;
                    }
                    for (TLRPC.PhotoSize size2 : newMsg.media.photo.sizes) {
                        if (size.type.equals(size2.type)) {
                            String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                            String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                            if (fileName.equals(fileName2)) {
                                break;
                            }
                            File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName + ".jpg");
                            File cacheFile2 = new File(AndroidUtilities.getCacheDir(), fileName2 + ".jpg");
                            cacheFile.renameTo(cacheFile2);
                            FileLoader.getInstance().replaceImageInCache(fileName, fileName2);
                            size2.location = size.location;
                            break;
                        }
                    }
                }
                sentMessage.message = newMsg.message;
                sentMessage.attachPath = newMsg.attachPath;
                newMsg.media.photo.id = sentMessage.media.photo.id;
                newMsg.media.photo.access_hash = sentMessage.media.photo.access_hash;
            } else if (sentMessage.media instanceof TLRPC.TL_messageMediaVideo && sentMessage.media.video != null && newMsg.media instanceof TLRPC.TL_messageMediaVideo && newMsg.media.video != null) {
                MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.video, 2);

                TLRPC.PhotoSize size2 = newMsg.media.video.thumb;
                TLRPC.PhotoSize size = sentMessage.media.video.thumb;
                if (size2.location != null && size.location != null && !(size instanceof TLRPC.TL_photoSizeEmpty) && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
                    String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                    String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                    if (!fileName.equals(fileName2)) {
                        File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName + ".jpg");
                        File cacheFile2 = new File(AndroidUtilities.getCacheDir(), fileName2 + ".jpg");
                        boolean result = cacheFile.renameTo(cacheFile2);
                        FileLoader.getInstance().replaceImageInCache(fileName, fileName2);
                        size2.location = size.location;
                    }
                }
                sentMessage.message = newMsg.message;
                sentMessage.attachPath = newMsg.attachPath;
                newMsg.media.video.dc_id = sentMessage.media.video.dc_id;
                newMsg.media.video.id = sentMessage.media.video.id;
                newMsg.media.video.access_hash = sentMessage.media.video.access_hash;
            } else if (sentMessage.media instanceof TLRPC.TL_messageMediaDocument && sentMessage.media.document != null && newMsg.media instanceof TLRPC.TL_messageMediaDocument && newMsg.media.document != null) {
                MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.document, 1);

                TLRPC.PhotoSize size2 = newMsg.media.document.thumb;
                TLRPC.PhotoSize size = sentMessage.media.document.thumb;
                if (size2.location != null && size.location != null && !(size instanceof TLRPC.TL_photoSizeEmpty) && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
                    String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                    String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                    if (!fileName.equals(fileName2)) {
                        File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName + ".jpg");
                        File cacheFile2 = new File(AndroidUtilities.getCacheDir(), fileName2 + ".jpg");
                        boolean result = cacheFile.renameTo(cacheFile2);
                        FileLoader.getInstance().replaceImageInCache(fileName, fileName2);
                        size2.location = size.location;
                    }
                }
                if (newMsg.attachPath != null && newMsg.attachPath.startsWith(AndroidUtilities.getCacheDir().getAbsolutePath())) {
                    File cacheFile = new File(newMsg.attachPath);
                    File cacheFile2 = new File(AndroidUtilities.getCacheDir(), MessageObject.getAttachFileName(sentMessage.media.document));
                    boolean result = cacheFile.renameTo(cacheFile2);
                    if (result) {
                        newMsg.attachPath = null;
                    } else {
                        sentMessage.attachPath = newMsg.attachPath;
                        sentMessage.message = newMsg.message;
                    }
                } else {
                    sentMessage.attachPath = newMsg.attachPath;
                    sentMessage.message = newMsg.message;
                }
                newMsg.media.document.dc_id = sentMessage.media.document.dc_id;
                newMsg.media.document.id = sentMessage.media.document.id;
                newMsg.media.document.access_hash = sentMessage.media.document.access_hash;
            } else if (sentMessage.media instanceof TLRPC.TL_messageMediaAudio && sentMessage.media.audio != null && newMsg.media instanceof TLRPC.TL_messageMediaAudio && newMsg.media.audio != null) {
                sentMessage.message = newMsg.message;
                sentMessage.attachPath = newMsg.attachPath;

                String fileName = newMsg.media.audio.dc_id + "_" + newMsg.media.audio.id + ".m4a";
                String fileName2 = sentMessage.media.audio.dc_id + "_" + sentMessage.media.audio.id + ".m4a";
                if (!fileName.equals(fileName2)) {
                    File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName);
                    File cacheFile2 = new File(AndroidUtilities.getCacheDir(), fileName2);
                    cacheFile.renameTo(cacheFile2);
                }
                newMsg.media.audio.dc_id = sentMessage.media.audio.dc_id;
                newMsg.media.audio.id = sentMessage.media.audio.id;
                newMsg.media.audio.access_hash = sentMessage.media.audio.access_hash;
            }
        } else if (file != null) {
            if (newMsg.media instanceof TLRPC.TL_messageMediaPhoto && newMsg.media.photo != null) {
                TLRPC.PhotoSize size = newMsg.media.photo.sizes.get(newMsg.media.photo.sizes.size() - 1);
                String fileName = size.location.volume_id + "_" + size.location.local_id;
                size.location = new TLRPC.TL_fileEncryptedLocation();
                size.location.key = decryptedMessage.media.key;
                size.location.iv = decryptedMessage.media.iv;
                size.location.dc_id = file.dc_id;
                size.location.volume_id = file.id;
                size.location.secret = file.access_hash;
                size.location.local_id = file.key_fingerprint;
                String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName + ".jpg");
                File cacheFile2 = new File(AndroidUtilities.getCacheDir(), fileName2 + ".jpg");
                boolean result = cacheFile.renameTo(cacheFile2);
                FileLoader.getInstance().replaceImageInCache(fileName, fileName2);
                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true);

                MessagesStorage.getInstance().putSentFile(originalPath, newMsg.media.photo, 3);
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaVideo && newMsg.media.video != null) {
                TLRPC.Video video = newMsg.media.video;
                newMsg.media.video = new TLRPC.TL_videoEncrypted();
                newMsg.media.video.duration = video.duration;
                newMsg.media.video.thumb = video.thumb;
                newMsg.media.video.dc_id = file.dc_id;
                newMsg.media.video.w = video.w;
                newMsg.media.video.h = video.h;
                newMsg.media.video.date = video.date;
                newMsg.media.video.caption = "";
                newMsg.media.video.user_id = video.user_id;
                newMsg.media.video.size = file.size;
                newMsg.media.video.id = file.id;
                newMsg.media.video.access_hash = file.access_hash;
                newMsg.media.video.key = decryptedMessage.media.key;
                newMsg.media.video.iv = decryptedMessage.media.iv;
                newMsg.media.video.path = video.path;
                newMsg.media.video.mime_type = video.mime_type;
                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true);

                MessagesStorage.getInstance().putSentFile(originalPath, newMsg.media.video, 5);
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaDocument && newMsg.media.document != null) {
                TLRPC.Document document = newMsg.media.document;
                newMsg.media.document = new TLRPC.TL_documentEncrypted();
                newMsg.media.document.id = file.id;
                newMsg.media.document.access_hash = file.access_hash;
                newMsg.media.document.user_id = document.user_id;
                newMsg.media.document.date = document.date;
                newMsg.media.document.file_name = document.file_name;
                newMsg.media.document.mime_type = document.mime_type;
                newMsg.media.document.size = file.size;
                newMsg.media.document.key = decryptedMessage.media.key;
                newMsg.media.document.iv = decryptedMessage.media.iv;
                newMsg.media.document.path = document.path;
                newMsg.media.document.thumb = document.thumb;
                newMsg.media.document.dc_id = file.dc_id;

                if (document.path != null && document.path.startsWith(AndroidUtilities.getCacheDir().getAbsolutePath())) {
                    File cacheFile = new File(document.path);
                    File cacheFile2 = new File(AndroidUtilities.getCacheDir(), MessageObject.getAttachFileName(newMsg.media.document));
                    cacheFile.renameTo(cacheFile2);
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true);

                MessagesStorage.getInstance().putSentFile(originalPath, newMsg.media.document, 4);
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaAudio && newMsg.media.audio != null) {
                TLRPC.Audio audio = newMsg.media.audio;
                newMsg.media.audio = new TLRPC.TL_audioEncrypted();
                newMsg.media.audio.id = file.id;
                newMsg.media.audio.access_hash = file.access_hash;
                newMsg.media.audio.user_id = audio.user_id;
                newMsg.media.audio.date = audio.date;
                newMsg.media.audio.duration = audio.duration;
                newMsg.media.audio.size = file.size;
                newMsg.media.audio.dc_id = file.dc_id;
                newMsg.media.audio.key = decryptedMessage.media.key;
                newMsg.media.audio.iv = decryptedMessage.media.iv;
                newMsg.media.audio.path = audio.path;
                newMsg.media.audio.mime_type = audio.mime_type;

                String fileName = audio.dc_id + "_" + audio.id + ".m4a";
                String fileName2 = newMsg.media.audio.dc_id + "_" + newMsg.media.audio.id + ".m4a";
                if (!fileName.equals(fileName2)) {
                    File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName);
                    File cacheFile2 = new File(AndroidUtilities.getCacheDir(), fileName2);
                    cacheFile.renameTo(cacheFile2);
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true);
            }
        }
    }

    private void performSendEncryptedRequest(final TLRPC.DecryptedMessage req, final MessageObject newMsgObj, final TLRPC.EncryptedChat chat, final TLRPC.InputEncryptedFile encryptedFile, final String originalPath) {
        if (req == null || chat.auth_key == null || chat instanceof TLRPC.TL_encryptedChatRequested || chat instanceof TLRPC.TL_encryptedChatWaiting) {
            return;
        }
        int len = req.getObjectSize();
        ByteBufferDesc toEncrypt = BuffersStorage.getInstance().getFreeBuffer(4 + len);
        toEncrypt.writeInt32(len);
        req.serializeToStream(toEncrypt);

        byte[] messageKeyFull = Utilities.computeSHA1(toEncrypt.buffer);
        byte[] messageKey = new byte[16];
        System.arraycopy(messageKeyFull, messageKeyFull.length - 16, messageKey, 0, 16);

        MessageKeyData keyData = Utilities.generateMessageKeyData(chat.auth_key, messageKey, false);

        len = toEncrypt.length();
        int extraLen = len % 16 != 0 ? 16 - len % 16 : 0;
        ByteBufferDesc dataForEncryption = BuffersStorage.getInstance().getFreeBuffer(len + extraLen);
        toEncrypt.position(0);
        dataForEncryption.writeRaw(toEncrypt);
        if (extraLen != 0) {
            byte[] b = new byte[extraLen];
            Utilities.random.nextBytes(b);
            dataForEncryption.writeRaw(b);
        }
        BuffersStorage.getInstance().reuseFreeBuffer(toEncrypt);

        Utilities.aesIgeEncryption(dataForEncryption.buffer, keyData.aesKey, keyData.aesIv, true, false, 0, dataForEncryption.limit());

        ByteBufferDesc data = BuffersStorage.getInstance().getFreeBuffer(8 + messageKey.length + dataForEncryption.length());
        dataForEncryption.position(0);
        data.writeInt64(chat.key_fingerprint);
        data.writeRaw(messageKey);
        data.writeRaw(dataForEncryption);
        BuffersStorage.getInstance().reuseFreeBuffer(dataForEncryption);
        data.position(0);

        TLObject reqToSend = null;

        if (encryptedFile == null) {
            TLRPC.TL_messages_sendEncrypted req2 = new TLRPC.TL_messages_sendEncrypted();
            req2.data = data;
            req2.random_id = req.random_id;
            req2.peer = new TLRPC.TL_inputEncryptedChat();
            req2.peer.chat_id = chat.id;
            req2.peer.access_hash = chat.access_hash;
            reqToSend = req2;
        } else {
            TLRPC.TL_messages_sendEncryptedFile req2 = new TLRPC.TL_messages_sendEncryptedFile();
            req2.data = data;
            req2.random_id = req.random_id;
            req2.peer = new TLRPC.TL_inputEncryptedChat();
            req2.peer.chat_id = chat.id;
            req2.peer.access_hash = chat.access_hash;
            req2.file = encryptedFile;
            reqToSend = req2;
        }
        ConnectionsManager.getInstance().performRpc(reqToSend, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (newMsgObj != null) {
                    if (error == null) {
                        final TLRPC.messages_SentEncryptedMessage res = (TLRPC.messages_SentEncryptedMessage) response;
                        newMsgObj.messageOwner.date = res.date;
                        if (res.file instanceof TLRPC.TL_encryptedFile) {
                            processSentMessage(newMsgObj.messageOwner, null, res.file, req, originalPath);
                        }
                        MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                MessagesStorage.getInstance().updateMessageStateAndId(newMsgObj.messageOwner.random_id, newMsgObj.messageOwner.id, newMsgObj.messageOwner.id, res.date, false);
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENT;
                                        NotificationCenter.getInstance().postNotificationName(messageReceivedByServer, newMsgObj.messageOwner.id, newMsgObj.messageOwner.id, newMsgObj);
                                        sendingMessages.remove(newMsgObj.messageOwner.id);
                                    }
                                });
                            }
                        });
                    } else {
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                sendingMessages.remove(newMsgObj.messageOwner.id);
                                newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SEND_ERROR;
                                NotificationCenter.getInstance().postNotificationName(messageSendError, newMsgObj.messageOwner.id);
                            }
                        });
                    }
                }
            }
        });
    }

    private void performSendMessageRequest(TLObject req, final MessageObject newMsgObj, final String originalPath) {
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    final int oldId = newMsgObj.messageOwner.id;
                    final ArrayList<TLRPC.Message> sentMessages = new ArrayList<TLRPC.Message>();

                    if (response instanceof TLRPC.TL_messages_sentMessage) {
                        TLRPC.TL_messages_sentMessage res = (TLRPC.TL_messages_sentMessage) response;
                        newMsgObj.messageOwner.id = res.id;
                        if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                            MessagesStorage.lastSeqValue = res.seq;
                            MessagesStorage.lastDateValue = res.date;
                            MessagesStorage.lastPtsValue = res.pts;
                            MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                        } else if (MessagesStorage.lastSeqValue != res.seq) {
                            FileLog.e("tmessages", "need get diff TL_messages_sentMessage, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                            if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                                if (updatesStartWaitTime == 0) {
                                    updatesStartWaitTime = System.currentTimeMillis();
                                }
                                FileLog.e("tmessages", "add TL_messages_sentMessage to queue");
                                UserActionUpdates updates = new UserActionUpdates();
                                updates.seq = res.seq;
                                updatesQueue.add(updates);
                            } else {
                                getDifference();
                            }
                        }
                    } else if (response instanceof TLRPC.messages_StatedMessage) {
                        TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                        sentMessages.add(res.message);
                        newMsgObj.messageOwner.id = res.message.id;
                        processSentMessage(newMsgObj.messageOwner, res.message, null, null, originalPath);
                        if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                            MessagesStorage.lastSeqValue = res.seq;
                            MessagesStorage.lastPtsValue = res.pts;
                            MessagesStorage.lastDateValue = res.message.date;
                            MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                        } else if (MessagesStorage.lastSeqValue != res.seq) {
                            FileLog.e("tmessages", "need get diff messages_StatedMessage, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                            if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                                if (updatesStartWaitTime == 0) {
                                    updatesStartWaitTime = System.currentTimeMillis();
                                }
                                FileLog.e("tmessages", "add messages_StatedMessage to queue");
                                UserActionUpdates updates = new UserActionUpdates();
                                updates.seq = res.seq;
                                updatesQueue.add(updates);
                            } else {
                                getDifference();
                            }
                        }
                    } else if (response instanceof TLRPC.messages_StatedMessages) {
                        TLRPC.messages_StatedMessages res = (TLRPC.messages_StatedMessages) response;
                        if (!res.messages.isEmpty()) {
                            TLRPC.Message message = res.messages.get(0);
                            newMsgObj.messageOwner.id = message.id;
                            sentMessages.add(message);
                            processSentMessage(newMsgObj.messageOwner, message, null, null, originalPath);
                        }
                        if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                            MessagesStorage.lastSeqValue = res.seq;
                            MessagesStorage.lastPtsValue = res.pts;
                            MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                        } else if (MessagesStorage.lastSeqValue != res.seq) {
                            FileLog.e("tmessages", "need get diff messages_StatedMessages, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                            if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                                if (updatesStartWaitTime == 0) {
                                    updatesStartWaitTime = System.currentTimeMillis();
                                }
                                FileLog.e("tmessages", "add messages_StatedMessages to queue");
                                UserActionUpdates updates = new UserActionUpdates();
                                updates.seq = res.seq;
                                updatesQueue.add(updates);
                            } else {
                                getDifference();
                            }
                        }
                    }
                    MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            MessagesStorage.getInstance().updateMessageStateAndId(newMsgObj.messageOwner.random_id, oldId, newMsgObj.messageOwner.id, 0, false);
                            MessagesStorage.getInstance().putMessages(sentMessages, true, false);
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENT;
                                    NotificationCenter.getInstance().postNotificationName(messageReceivedByServer, oldId, newMsgObj.messageOwner.id, newMsgObj);
                                    sendingMessages.remove(oldId);
                                }
                            });
                        }
                    });
                } else {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            sendingMessages.remove(newMsgObj.messageOwner.id);
                            newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SEND_ERROR;
                            NotificationCenter.getInstance().postNotificationName(messageSendError, newMsgObj.messageOwner.id);
                        }
                    });
                }
            }
        }, (req instanceof TLRPC.TL_messages_forwardMessages ? null : new RPCRequest.RPCQuickAckDelegate() {
            @Override
            public void quickAck() {
                final int msg_id = newMsgObj.messageOwner.id;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENT;
                        NotificationCenter.getInstance().postNotificationName(messageReceivedByAck, msg_id);
                    }
                });
            }
        }), true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassCanCompress, ConnectionsManager.DEFAULT_DATACENTER_ID);
    }

    private void putToDelayedMessages(String location, DelayedMessage message) {
        ArrayList<DelayedMessage> arrayList = delayedMessages.get(location);
        if (arrayList == null) {
            arrayList = new ArrayList<DelayedMessage>();
            delayedMessages.put(location, arrayList);
        }
        arrayList.add(message);
    }

    private void performSendDelayedMessage(final DelayedMessage message) {
        if (message.type == 0) {
            String location = AndroidUtilities.getCacheDir() + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
            putToDelayedMessages(location, message);
            if (message.sendRequest != null) {
                FileLoader.getInstance().uploadFile(location, false);
            } else {
                FileLoader.getInstance().uploadFile(location, true);
            }
        } else if (message.type == 1) {
            if (message.sendRequest != null) {
                if (message.sendRequest.media.thumb == null) {
                    String location = AndroidUtilities.getCacheDir() + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance().uploadFile(location, false);
                } else {
                    String location = message.videoLocation.path;
                    if (location == null) {
                        location = AndroidUtilities.getCacheDir() + "/" + message.videoLocation.id + ".mp4";
                    }
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance().uploadFile(location, false);
                }
            } else {
                String location = message.videoLocation.path;
                if (location == null) {
                    location = AndroidUtilities.getCacheDir() + "/" + message.videoLocation.id + ".mp4";
                }
                putToDelayedMessages(location, message);
                FileLoader.getInstance().uploadFile(location, true);
            }
        } else if (message.type == 2) {
            if (message.sendRequest != null && message.sendRequest.media.thumb == null && message.location != null) {
                String location = AndroidUtilities.getCacheDir() + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                putToDelayedMessages(location, message);
                FileLoader.getInstance().uploadFile(location, false);
            } else {
                String location = message.documentLocation.path;
                putToDelayedMessages(location, message);
                if (message.sendRequest != null) {
                    FileLoader.getInstance().uploadFile(location, false);
                } else {
                    FileLoader.getInstance().uploadFile(location, true);
                }
            }
        } else if (message.type == 3) {
            String location = message.audioLocation.path;
            putToDelayedMessages(location, message);
            if (message.sendRequest != null) {
                FileLoader.getInstance().uploadFile(location, false);
            } else {
                FileLoader.getInstance().uploadFile(location, true);
            }
        }
    }

    public long createChat(String title, ArrayList<Integer> selectedContacts, final TLRPC.InputFile uploadedAvatar) {
        TLRPC.TL_messages_createChat req = new TLRPC.TL_messages_createChat();
        req.title = title;
        for (Integer uid : selectedContacts) {
            TLRPC.User user = users.get(uid);
            if (user == null) {
                continue;
            }
            req.users.add(getInputUser(user));
        }
        return ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(chatDidFailCreate);
                        }
                    });
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.getClientUserId()) {
                                UserConfig.setCurrentUser(user);
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.getInstance().postNotificationName(chatDidCreated, chat.id);
                        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                        if (uploadedAvatar != null) {
                            changeChatAvatar(chat.id, uploadedAvatar);
                        }
                    }
                });

                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.getInstance().putMessages(messages, true, true);
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    FileLog.e("tmessages", "need get diff TL_messages_createChat, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_messages_createChat to queue");
                        UserActionUpdates updates = new UserActionUpdates();
                        updates.seq = res.seq;
                        updatesQueue.add(updates);
                    } else {
                        getDifference();
                    }
                }
            }
        });
    }

    public void addUserToChat(int chat_id, final TLRPC.User user, final TLRPC.ChatParticipants info, int count_fwd) {
        if (user == null) {
            return;
        }

        TLRPC.TL_messages_addChatUser req = new TLRPC.TL_messages_addChatUser();
        req.chat_id = chat_id;
        req.fwd_limit = count_fwd;
        req.user_id = getInputUser(user);

        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }

                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.getClientUserId()) {
                                UserConfig.setCurrentUser(user);
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        chats.put(chat.id, chat);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.getInstance().postNotificationName(updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
                        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);

                        if (info != null) {
                            for (TLRPC.TL_chatParticipant p : info.participants) {
                                if (p.user_id == user.id) {
                                    return;
                                }
                            }
                            TLRPC.TL_chatParticipant newPart = new TLRPC.TL_chatParticipant();
                            newPart.user_id = user.id;
                            newPart.inviter_id = UserConfig.getClientUserId();
                            newPart.date = ConnectionsManager.getInstance().getCurrentTime();
                            info.participants.add(0, newPart);
                            MessagesStorage.getInstance().updateChatInfo(info.chat_id, info, true);
                            NotificationCenter.getInstance().postNotificationName(chatInfoDidLoaded, info.chat_id, info);
                        }
                    }
                });

                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.getInstance().putMessages(messages, true, true);
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    FileLog.e("tmessages", "need get diff TL_messages_addChatUser, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_messages_addChatUser to queue");
                        UserActionUpdates updates = new UserActionUpdates();
                        updates.seq = res.seq;
                        updatesQueue.add(updates);
                    } else {
                        getDifference();
                    }
                }
            }
        });
    }

    public void deleteUserFromChat(int chat_id, final TLRPC.User user, final TLRPC.ChatParticipants info) {
        if (user == null) {
            return;
        }
        TLRPC.TL_messages_deleteChatUser req = new TLRPC.TL_messages_deleteChatUser();
        req.chat_id = chat_id;
        req.user_id = getInputUser(user);
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.getClientUserId()) {
                                UserConfig.setCurrentUser(user);
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        if (user.id != UserConfig.getClientUserId()) {
                            final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                            messagesObj.add(new MessageObject(res.message, users));
                            TLRPC.Chat chat = res.chats.get(0);
                            chats.put(chat.id, chat);
                            updateInterfaceWithMessages(-chat.id, messagesObj);
                            NotificationCenter.getInstance().postNotificationName(updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
                            NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                        }
                        boolean changed = false;
                        if (info != null) {
                            for (int a = 0; a < info.participants.size(); a++) {
                                TLRPC.TL_chatParticipant p = info.participants.get(a);
                                if (p.user_id == user.id) {
                                    info.participants.remove(a);
                                    changed = true;
                                    break;
                                }
                            }
                            if (changed) {
                                MessagesStorage.getInstance().updateChatInfo(info.chat_id, info, true);
                                NotificationCenter.getInstance().postNotificationName(chatInfoDidLoaded, info.chat_id, info);
                            }
                        }
                    }
                });

                if (user.id != UserConfig.getClientUserId()) {
                    final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                    messages.add(res.message);
                    MessagesStorage.getInstance().putMessages(messages, true, true);
                }
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    FileLog.e("tmessages", "need get diff TL_messages_deleteChatUser, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_messages_deleteChatUser to queue");
                        UserActionUpdates updates = new UserActionUpdates();
                        updates.seq = res.seq;
                        updatesQueue.add(updates);
                    } else {
                        getDifference();
                    }
                }
            }
        });
    }

    public void changeChatTitle(int chat_id, String title) {
        TLRPC.TL_messages_editChatTitle req = new TLRPC.TL_messages_editChatTitle();
        req.chat_id = chat_id;
        req.title = title;
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.getClientUserId()) {
                                UserConfig.setCurrentUser(user);
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        chats.put(chat.id, chat);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                        NotificationCenter.getInstance().postNotificationName(updateInterfaces, UPDATE_MASK_CHAT_NAME);
                    }
                });

                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.getInstance().putMessages(messages, true, true);
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    FileLog.e("tmessages", "need get diff TL_messages_editChatTitle, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_messages_editChatTitle to queue");
                        UserActionUpdates updates = new UserActionUpdates();
                        updates.seq = res.seq;
                        updatesQueue.add(updates);
                    } else {
                        getDifference();
                    }
                }
            }
        });
    }

    public void changeChatAvatar(int chat_id, TLRPC.InputFile uploadedAvatar) {
        TLRPC.TL_messages_editChatPhoto req2 = new TLRPC.TL_messages_editChatPhoto();
        req2.chat_id = chat_id;
        if (uploadedAvatar != null) {
            req2.photo = new TLRPC.TL_inputChatUploadedPhoto();
            req2.photo.file = uploadedAvatar;
            req2.photo.crop = new TLRPC.TL_inputPhotoCropAuto();
        } else {
            req2.photo = new TLRPC.TL_inputChatPhotoEmpty();
        }
        ConnectionsManager.getInstance().performRpc(req2, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.getClientUserId()) {
                                UserConfig.setCurrentUser(user);
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        chats.put(chat.id, chat);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                        NotificationCenter.getInstance().postNotificationName(updateInterfaces, UPDATE_MASK_CHAT_AVATAR);
                    }
                });

                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.getInstance().putMessages(messages, true, true);
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    FileLog.e("tmessages", "need get diff TL_messages_editChatPhoto, seq: " + MessagesStorage.lastSeqValue + " " + res.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_messages_editChatPhoto to queue");
                        UserActionUpdates updates = new UserActionUpdates();
                        updates.seq = res.seq;
                        updatesQueue.add(updates);
                    } else {
                        getDifference();
                    }
                }
            }
        });
    }

    public void unregistedPush() {
        if (UserConfig.registeredForPush && UserConfig.pushString.length() == 0) {
            TLRPC.TL_account_unregisterDevice req = new TLRPC.TL_account_unregisterDevice();
            req.token = UserConfig.pushString;
            req.token_type = 2;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
    }

    public void logOut() {
        TLRPC.TL_auth_logOut req = new TLRPC.TL_auth_logOut();
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                ConnectionsManager.getInstance().cleanUp();
            }
        });
    }

    public void registerForPush(final String regid) {
        if (regid == null || regid.length() == 0 || registeringForPush || UserConfig.getClientUserId() == 0) {
            return;
        }
        if (UserConfig.registeredForPush && regid.equals(UserConfig.pushString)) {
            return;
        }
        registeringForPush = true;
        TLRPC.TL_account_registerDevice req = new TLRPC.TL_account_registerDevice();
        req.token_type = 2;
        req.token = regid;
        req.app_sandbox = false;
        try {
            req.lang_code = Locale.getDefault().getCountry();
            req.device_model = Build.MANUFACTURER + Build.MODEL;
            if (req.device_model == null) {
                req.device_model = "Android unknown";
            }
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            req.app_version = pInfo.versionName + " (" + pInfo.versionCode + ")";
            if (req.app_version == null) {
                req.app_version = "App version unknown";
            }

        } catch (Exception e) {
            FileLog.e("tmessages", e);
            req.lang_code = "en";
            req.device_model = "Android unknown";
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            req.app_version = "App version unknown";
        }

        if (req.lang_code == null || req.lang_code.length() == 0) {
            req.lang_code = "en";
        }
        if (req.device_model == null || req.device_model.length() == 0) {
            req.device_model = "Android unknown";
        }
        if (req.app_version == null || req.app_version.length() == 0) {
            req.app_version = "App version unknown";
        }
        if (req.system_version == null || req.system_version.length() == 0) {
            req.system_version = "SDK Unknown";
        }

        if (req.app_version != null) {
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        FileLog.e("tmessages", "registered for push");
                        UserConfig.registeredForPush = true;
                        UserConfig.pushString = regid;
                        UserConfig.saveConfig(false);
                    }
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            registeringForPush = false;
                        }
                    });
                }
            });
        }
    }

    public void loadCurrentState() {
        if (updatingState) {
            return;
        }
        updatingState = true;
        TLRPC.TL_updates_getState req = new TLRPC.TL_updates_getState();
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                updatingState = false;
                if (error == null) {
                    TLRPC.TL_updates_state res = (TLRPC.TL_updates_state) response;
                    MessagesStorage.lastDateValue = res.date;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastQtsValue = res.qts;
                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else {
                    if (error.code != 401) {
                        loadCurrentState();
                    }
                }
            }
        });
    }

    private int getUpdateSeq(TLRPC.Updates updates) {
        if (updates instanceof TLRPC.TL_updatesCombined) {
            return updates.seq_start;
        } else {
            return updates.seq;
        }
    }

    private void processUpdatesQueue(boolean getDifference) {
        if (!updatesQueue.isEmpty()) {
            Collections.sort(updatesQueue, new Comparator<TLRPC.Updates>() {
                @Override
                public int compare(TLRPC.Updates updates, TLRPC.Updates updates2) {
                    int seq1 = getUpdateSeq(updates);
                    int seq2 = getUpdateSeq(updates2);
                    if (seq1 == seq2) {
                        return 0;
                    } else if (seq1 > seq2) {
                        return 1;
                    }
                    return -1;
                }
            });
            boolean anyProceed = false;
            for (int a = 0; a < updatesQueue.size(); a++) {
                TLRPC.Updates updates = updatesQueue.get(a);
                int seq = getUpdateSeq(updates);
                if (MessagesStorage.lastSeqValue + 1 == seq || MessagesStorage.lastSeqValue == seq) {
                    processUpdates(updates, true);
                    anyProceed = true;
                    updatesQueue.remove(a);
                    a--;
                } else if (MessagesStorage.lastSeqValue < seq) {
                    if (updatesStartWaitTime != 0 && (anyProceed || updatesStartWaitTime + 1500 > System.currentTimeMillis())) {
                        FileLog.e("tmessages", "HOLE IN UPDATES QUEUE - will wait more time");
                        if (anyProceed) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        return;
                    } else {
                        FileLog.e("tmessages", "HOLE IN UPDATES QUEUE - getDifference");
                        updatesStartWaitTime = 0;
                        updatesQueue.clear();
                        getDifference();
                        return;
                    }
                } else {
                    updatesQueue.remove(a);
                    a--;
                }
            }
            updatesQueue.clear();
            FileLog.e("tmessages", "UPDATES QUEUE PROCEED - OK");
            updatesStartWaitTime = 0;
            if (getDifference) {
                final int stateCopy = ConnectionsManager.getInstance().getConnectionState();
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(703, stateCopy);
                    }
                });
            }
        } else {
            if (getDifference) {
                final int stateCopy = ConnectionsManager.getInstance().getConnectionState();
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(703, stateCopy);
                    }
                });
            } else {
                updatesStartWaitTime = 0;
            }
        }
    }

    public void getDifference() {
        registerForPush(UserConfig.pushString);
        if (MessagesStorage.lastDateValue == 0) {
            loadCurrentState();
            return;
        }
        if (gettingDifference) {
            return;
        }
        if (!firstGettingTask) {
            getNewDeleteTask(null);
            firstGettingTask = true;
        }
        gettingDifference = true;
        TLRPC.TL_updates_getDifference req = new TLRPC.TL_updates_getDifference();
        req.pts = MessagesStorage.lastPtsValue;
        req.date = MessagesStorage.lastDateValue;
        req.qts = MessagesStorage.lastQtsValue;
        FileLog.e("tmessages", "start getDifference with date = " + MessagesStorage.lastDateValue + " pts = " + MessagesStorage.lastPtsValue + " seq = " + MessagesStorage.lastSeqValue);
        if (ConnectionsManager.getInstance().getConnectionState() == 0) {
            ConnectionsManager.getInstance().setConnectionState(3);
            final int stateCopy = ConnectionsManager.getInstance().getConnectionState();
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(703, stateCopy);
                }
            });
        }
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                gettingDifferenceAgain = false;
                if (error == null) {
                    final TLRPC.updates_Difference res = (TLRPC.updates_Difference) response;
                    gettingDifferenceAgain = res instanceof TLRPC.TL_updates_differenceSlice;

                    final HashMap<Integer, TLRPC.User> usersDict = new HashMap<Integer, TLRPC.User>();
                    for (TLRPC.User user : res.users) {
                        usersDict.put(user.id, user);
                    }

                    final ArrayList<TLRPC.TL_updateMessageID> msgUpdates = new ArrayList<TLRPC.TL_updateMessageID>();
                    if (!res.other_updates.isEmpty()) {
                        for (int a = 0; a < res.other_updates.size(); a++) {
                            TLRPC.Update upd = res.other_updates.get(a);
                            if (upd instanceof TLRPC.TL_updateMessageID) {
                                msgUpdates.add((TLRPC.TL_updateMessageID) upd);
                                res.other_updates.remove(a);
                                a--;
                            }
                        }
                    }

                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            for (TLRPC.User user : res.users) {
                                users.put(user.id, user);
                                if (user.id == UserConfig.getClientUserId()) {
                                    UserConfig.setCurrentUser(user);
                                }
                            }
                            for (TLRPC.Chat chat : res.chats) {
                                chats.put(chat.id, chat);
                            }
                        }
                    });

                    MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            if (!msgUpdates.isEmpty()) {
                                final HashMap<Integer, Integer> corrected = new HashMap<Integer, Integer>();
                                for (TLRPC.TL_updateMessageID update : msgUpdates) {
                                    Integer oldId = MessagesStorage.getInstance().updateMessageStateAndId(update.random_id, null, update.id, 0, false);
                                    if (oldId != null) {
                                        corrected.put(oldId, update.id);
                                    }
                                }

                                if (!corrected.isEmpty()) {
                                    Utilities.RunOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            for (HashMap.Entry<Integer, Integer> entry : corrected.entrySet()) {
                                                Integer oldId = entry.getKey();
                                                sendingMessages.remove(oldId);
                                                Integer newId = entry.getValue();
                                                NotificationCenter.getInstance().postNotificationName(messageReceivedByServer, oldId, newId, null);
                                            }
                                        }
                                    });
                                }
                            }

                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    if (!res.new_messages.isEmpty() || !res.new_encrypted_messages.isEmpty()) {
                                        final HashMap<Long, ArrayList<MessageObject>> messages = new HashMap<Long, ArrayList<MessageObject>>();
                                        for (TLRPC.EncryptedMessage encryptedMessage : res.new_encrypted_messages) {
                                            TLRPC.Message message = decryptMessage(encryptedMessage);
                                            if (message != null) {
                                                res.new_messages.add(message);
                                            }
                                        }

                                        final ArrayList<MessageObject> pushMessages = new ArrayList<MessageObject>();
                                        for (TLRPC.Message message : res.new_messages) {
                                            MessageObject obj = new MessageObject(message, usersDict);

                                            long dialog_id = obj.messageOwner.dialog_id;
                                            if (dialog_id == 0) {
                                                if (obj.messageOwner.to_id.chat_id != 0) {
                                                    dialog_id = -obj.messageOwner.to_id.chat_id;
                                                } else {
                                                    dialog_id = obj.messageOwner.to_id.user_id;
                                                }
                                            }

                                            if (!obj.isFromMe() && obj.isUnread()) {
                                                pushMessages.add(obj);
                                            }

                                            long uid;
                                            if (message.dialog_id != 0) {
                                                uid = message.dialog_id;
                                            } else {
                                                if (message.to_id.chat_id != 0) {
                                                    uid = -message.to_id.chat_id;
                                                } else {
                                                    if (message.to_id.user_id == UserConfig.getClientUserId()) {
                                                        message.to_id.user_id = message.from_id;
                                                    }
                                                    uid = message.to_id.user_id;
                                                }
                                            }
                                            ArrayList<MessageObject> arr = messages.get(uid);
                                            if (arr == null) {
                                                arr = new ArrayList<MessageObject>();
                                                messages.put(uid, arr);
                                            }
                                            arr.add(obj);
                                        }

                                        processPendingEncMessages();

                                        Utilities.RunOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : messages.entrySet()) {
                                                    Long key = pair.getKey();
                                                    ArrayList<MessageObject> value = pair.getValue();
                                                    updateInterfaceWithMessages(key, value);
                                                }
                                                NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                                                if (!pushMessages.isEmpty()) {
                                                    NotificationsController.getInstance().processNewMessages(pushMessages, !(res instanceof TLRPC.TL_updates_differenceSlice));
                                                }
                                            }
                                        });
                                        MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                MessagesStorage.getInstance().startTransaction(false);
                                                MessagesStorage.getInstance().putMessages(res.new_messages, false, false);
                                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, false, false);
                                                MessagesStorage.getInstance().commitTransaction(false);
                                            }
                                        });
                                    }

                                    if (res != null && !res.other_updates.isEmpty()) {
                                        processUpdateArray(res.other_updates, res.users, res.chats);
                                    }

                                    gettingDifference = false;
                                    if (res instanceof TLRPC.TL_updates_difference) {
                                        MessagesStorage.lastSeqValue = res.state.seq;
                                        MessagesStorage.lastDateValue = res.state.date;
                                        MessagesStorage.lastPtsValue = res.state.pts;
                                        MessagesStorage.lastQtsValue = res.state.qts;
                                        ConnectionsManager.getInstance().setConnectionState(0);
                                        processUpdatesQueue(true);
                                    } else if (res instanceof TLRPC.TL_updates_differenceSlice) {
                                        MessagesStorage.lastDateValue = res.intermediate_state.date;
                                        MessagesStorage.lastPtsValue = res.intermediate_state.pts;
                                        MessagesStorage.lastQtsValue = res.intermediate_state.qts;
                                        gettingDifferenceAgain = true;
                                        getDifference();
                                    } else if (res instanceof TLRPC.TL_updates_differenceEmpty) {
                                        MessagesStorage.lastSeqValue = res.seq;
                                        MessagesStorage.lastDateValue = res.date;
                                        ConnectionsManager.getInstance().setConnectionState(0);
                                        processUpdatesQueue(true);
                                    }
                                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                                    FileLog.e("tmessages", "received difference with date = " + MessagesStorage.lastDateValue + " pts = " + MessagesStorage.lastPtsValue + " seq = " + MessagesStorage.lastSeqValue);
                                    FileLog.e("tmessages", "messages = " + res.new_messages.size() + " users = " + res.users.size() + " chats = " + res.chats.size() + " other updates = " + res.other_updates.size());
                                }
                            });
                        }
                    });
                } else {
                    gettingDifference = false;
                    ConnectionsManager.getInstance().setConnectionState(0);
                    final int stateCopy = ConnectionsManager.getInstance().getConnectionState();
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(703, stateCopy);
                        }
                    });
                }
            }
        });
    }

    public void processUpdates(final TLRPC.Updates updates, boolean fromQueue) {
        boolean needGetDiff = false;
        boolean needReceivedQueue = false;
        boolean addedToQueue = false;
        if (updates instanceof TLRPC.TL_updateShort) {
            ArrayList<TLRPC.Update> arr = new ArrayList<TLRPC.Update>();
            arr.add(updates.update);
            processUpdateArray(arr, null, null);
        } else if (updates instanceof TLRPC.TL_updateShortChatMessage) {
            boolean missingData = chats.get(updates.chat_id) == null || users.get(updates.from_id) == null;
            if (missingData) {
                needGetDiff = true;
            } else {
                if (MessagesStorage.lastSeqValue + 1 == updates.seq) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.from_id = updates.from_id;
                    message.id = updates.id;
                    message.to_id = new TLRPC.TL_peerChat();
                    message.to_id.chat_id = updates.chat_id;
                    message.message = updates.message;
                    message.date = updates.date;
                    message.unread = true;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    MessagesStorage.lastSeqValue = updates.seq;
                    MessagesStorage.lastPtsValue = updates.pts;
                    final MessageObject obj = new MessageObject(message, null);
                    final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
                    objArr.add(obj);
                    ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                    arr.add(message);
                    final boolean printUpdate = updatePrintingUsersWithNewMessages(-updates.chat_id, objArr);
                    if (printUpdate) {
                        updatePrintingStrings();
                    }
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (printUpdate) {
                                NotificationCenter.getInstance().postNotificationName(updateInterfaces, UPDATE_MASK_USER_PRINT);
                            }
                            if (!obj.isFromMe() && obj.isUnread()) {
                                NotificationsController.getInstance().processNewMessages(objArr, true);
                            }
                            updateInterfaceWithMessages(-updates.chat_id, objArr);
                            NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                        }
                    });
                    MessagesStorage.getInstance().putMessages(arr, false, true);
                } else if (MessagesStorage.lastSeqValue != updates.seq) {
                    FileLog.e("tmessages", "need get diff TL_updateShortChatMessage, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_updateShortChatMessage to queue");
                        updatesQueue.add(updates);
                        addedToQueue = true;
                    } else {
                        needGetDiff = true;
                    }
                }
            }
        } else if (updates instanceof TLRPC.TL_updateShortMessage) {
            boolean missingData = users.get(updates.from_id) == null;
            if (missingData) {
                needGetDiff = true;
            } else {
                if (MessagesStorage.lastSeqValue + 1 == updates.seq) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.from_id = updates.from_id;
                    message.id = updates.id;
                    message.to_id = new TLRPC.TL_peerUser();
                    message.to_id.user_id = updates.from_id;
                    message.message = updates.message;
                    message.date = updates.date;
                    message.unread = true;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    MessagesStorage.lastSeqValue = updates.seq;
                    MessagesStorage.lastPtsValue = updates.pts;
                    MessagesStorage.lastDateValue = updates.date;
                    final MessageObject obj = new MessageObject(message, null);
                    final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
                    objArr.add(obj);
                    ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                    arr.add(message);
                    final boolean printUpdate = updatePrintingUsersWithNewMessages(updates.from_id, objArr);
                    if (printUpdate) {
                        updatePrintingStrings();
                    }
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (printUpdate) {
                                NotificationCenter.getInstance().postNotificationName(updateInterfaces, UPDATE_MASK_USER_PRINT);
                            }
                            if (!obj.isFromMe() && obj.isUnread()) {
                                NotificationsController.getInstance().processNewMessages(objArr, true);
                            }
                            updateInterfaceWithMessages(updates.from_id, objArr);
                            NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                        }
                    });
                    MessagesStorage.getInstance().putMessages(arr, false, true);
                } else if (MessagesStorage.lastSeqValue != updates.seq) {
                    FileLog.e("tmessages", "need get diff TL_updateShortMessage, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_updateShortMessage to queue");
                        updatesQueue.add(updates);
                        addedToQueue = true;
                    } else {
                        needGetDiff = true;
                    }
                }
            }
        } else if (updates instanceof TLRPC.TL_updatesCombined) {
            if (MessagesStorage.lastSeqValue + 1 == updates.seq_start || MessagesStorage.lastSeqValue == updates.seq_start) {
                MessagesStorage.getInstance().putUsersAndChats(updates.users, updates.chats, true, true);
                int lastPtsValue = MessagesStorage.lastPtsValue;
                int lastQtsValue = MessagesStorage.lastQtsValue;
                if (!processUpdateArray(updates.updates, updates.users, updates.chats)) {
                    MessagesStorage.lastPtsValue = lastPtsValue;
                    MessagesStorage.lastQtsValue = lastQtsValue;
                    FileLog.e("tmessages", "need get diff inner TL_updatesCombined, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                    needGetDiff = true;
                } else {
                    MessagesStorage.lastDateValue = updates.date;
                    MessagesStorage.lastSeqValue = updates.seq;
                    if (MessagesStorage.lastQtsValue != lastQtsValue) {
                        needReceivedQueue = true;
                    }
                }
            } else {
                FileLog.e("tmessages", "need get diff TL_updatesCombined, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq_start);
                if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                    if (updatesStartWaitTime == 0) {
                        updatesStartWaitTime = System.currentTimeMillis();
                    }
                    FileLog.e("tmessages", "add TL_updatesCombined to queue");
                    updatesQueue.add(updates);
                    addedToQueue = true;
                } else {
                    needGetDiff = true;
                }
            }
        } else if (updates instanceof TLRPC.TL_updates) {
            if (MessagesStorage.lastSeqValue + 1 == updates.seq || updates.seq == 0 || updates.seq == MessagesStorage.lastSeqValue) {
                MessagesStorage.getInstance().putUsersAndChats(updates.users, updates.chats, true, true);
                int lastPtsValue = MessagesStorage.lastPtsValue;
                int lastQtsValue = MessagesStorage.lastQtsValue;
                if (!processUpdateArray(updates.updates, updates.users, updates.chats)) {
                    needGetDiff = true;
                    MessagesStorage.lastPtsValue = lastPtsValue;
                    MessagesStorage.lastQtsValue = lastQtsValue;
                    FileLog.e("tmessages", "need get diff inner TL_updates, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                } else {
                    MessagesStorage.lastDateValue = updates.date;
                    if (updates.seq != 0) {
                        MessagesStorage.lastSeqValue = updates.seq;
                    }
                    if (MessagesStorage.lastQtsValue != lastQtsValue) {
                        needReceivedQueue = true;
                    }
                }
            } else {
                FileLog.e("tmessages", "need get diff TL_updates, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                    if (updatesStartWaitTime == 0) {
                        updatesStartWaitTime = System.currentTimeMillis();
                    }
                    FileLog.e("tmessages", "add TL_updates to queue");
                    updatesQueue.add(updates);
                    addedToQueue = true;
                } else {
                    needGetDiff = true;
                }
            }
        } else if (updates instanceof TLRPC.TL_updatesTooLong) {
            FileLog.e("tmessages", "need get diff TL_updatesTooLong");
            needGetDiff = true;
        } else if (updates instanceof UserActionUpdates) {
            MessagesStorage.lastSeqValue = updates.seq;
        }
        if (needGetDiff && !fromQueue) {
            getDifference();
        } else if (!fromQueue && !updatesQueue.isEmpty()) {
            processUpdatesQueue(false);
        }
        if (needReceivedQueue) {
            TLRPC.TL_messages_receivedQueue req = new TLRPC.TL_messages_receivedQueue();
            req.max_qts = MessagesStorage.lastQtsValue;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
        MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
    }

    public boolean processUpdateArray(ArrayList<TLRPC.Update> updates, final ArrayList<TLRPC.User> usersArr, final ArrayList<TLRPC.Chat> chatsArr) {
        if (updates.isEmpty()) {
            return true;
        }
        long currentTime = System.currentTimeMillis();

        final HashMap<Long, ArrayList<MessageObject>> messages = new HashMap<Long, ArrayList<MessageObject>>();
        final ArrayList<MessageObject> pushMessages = new ArrayList<MessageObject>();
        final ArrayList<TLRPC.Message> messagesArr = new ArrayList<TLRPC.Message>();
        final ArrayList<Integer> markAsReadMessages = new ArrayList<Integer>();
        final HashMap<Integer, Integer> markAsReadEncrypted = new HashMap<Integer, Integer>();
        final ArrayList<Integer> deletedMessages = new ArrayList<Integer>();
        boolean printChanged = false;
        final ArrayList<TLRPC.ChatParticipants> chatInfoToUpdate = new ArrayList<TLRPC.ChatParticipants>();
        final ArrayList<TLRPC.Update> updatesOnMainThread = new ArrayList<TLRPC.Update>();
        final ArrayList<TLRPC.TL_updateEncryptedMessagesRead> tasks = new ArrayList<TLRPC.TL_updateEncryptedMessagesRead>();
        final ArrayList<Integer> contactsIds = new ArrayList<Integer>();

        boolean checkForUsers = true;
        ConcurrentHashMap<Integer, TLRPC.User> usersDict;
        ConcurrentHashMap<Integer, TLRPC.Chat> chatsDict;
        if (usersArr != null) {
            usersDict = new ConcurrentHashMap<Integer, TLRPC.User>();
            for (TLRPC.User user : usersArr) {
                usersDict.put(user.id, user);
            }
        } else {
            checkForUsers = false;
            usersDict = users;
        }
        if (chatsArr != null) {
            chatsDict = new ConcurrentHashMap<Integer, TLRPC.Chat>();
            for (TLRPC.Chat chat : chatsArr) {
                chatsDict.put(chat.id, chat);
            }
        } else {
            checkForUsers = false;
            chatsDict = chats;
        }

        if (usersArr != null || chatsArr != null) {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (usersArr != null) {
                        for (TLRPC.User user : usersArr) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.getClientUserId()) {
                                UserConfig.setCurrentUser(user);
                            }
                        }
                    }
                    if (chatsArr != null) {
                        for (TLRPC.Chat chat : chatsArr) {
                            chats.put(chat.id, chat);
                        }
                    }
                }
            });
        }

        int interfaceUpdateMask = 0;

        for (TLRPC.Update update : updates) {
            if (update instanceof TLRPC.TL_updateNewMessage) {
                TLRPC.TL_updateNewMessage upd = (TLRPC.TL_updateNewMessage)update;
                if (checkForUsers) {
                    if (usersDict.get(upd.message.from_id) == null && users.get(upd.message.from_id) == null || upd.message.to_id.chat_id != 0 && chatsDict.get(upd.message.to_id.chat_id) == null && chats.get(upd.message.to_id.chat_id) == null) {
                        return false;
                    }
                }
                messagesArr.add(upd.message);
                MessageObject obj = new MessageObject(upd.message, usersDict);
                if (obj.type == 11) {
                    interfaceUpdateMask |= UPDATE_MASK_CHAT_AVATAR;
                } else if (obj.type == 10) {
                    interfaceUpdateMask |= UPDATE_MASK_CHAT_NAME;
                }
                long uid;
                if (upd.message.to_id.chat_id != 0) {
                    uid = -upd.message.to_id.chat_id;
                } else {
                    if (upd.message.to_id.user_id == UserConfig.getClientUserId()) {
                        upd.message.to_id.user_id = upd.message.from_id;
                    }
                    uid = upd.message.to_id.user_id;
                }
                ArrayList<MessageObject> arr = messages.get(uid);
                if (arr == null) {
                    arr = new ArrayList<MessageObject>();
                    messages.put(uid, arr);
                }
                arr.add(obj);
                MessagesStorage.lastPtsValue = update.pts;
                if (!obj.isFromMe() && obj.isUnread()) {
                    pushMessages.add(obj);
                }
            } else if (update instanceof TLRPC.TL_updateReadMessages) {
                markAsReadMessages.addAll(update.messages);
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateDeleteMessages) {
                deletedMessages.addAll(update.messages);
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateRestoreMessages) {
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateUserTyping || update instanceof TLRPC.TL_updateChatUserTyping) {
                if (update.user_id != UserConfig.getClientUserId()) {
                    long uid = -update.chat_id;
                    if (uid == 0) {
                        uid = update.user_id;
                    }
                    ArrayList<PrintingUser> arr = printingUsers.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<PrintingUser>();
                        printingUsers.put(uid, arr);
                    }
                    boolean exist = false;
                    for (PrintingUser u : arr) {
                        if (u.userId == update.user_id) {
                            exist = true;
                            u.lastTime = currentTime;
                            break;
                        }
                    }
                    if (!exist) {
                        PrintingUser newUser = new PrintingUser();
                        newUser.userId = update.user_id;
                        newUser.lastTime = currentTime;
                        arr.add(newUser);
                        printChanged = true;
                    }
                }
            } else if (update instanceof TLRPC.TL_updateChatParticipants) {
                interfaceUpdateMask |= UPDATE_MASK_CHAT_MEMBERS;
                chatInfoToUpdate.add(update.participants);
            } else if (update instanceof TLRPC.TL_updateUserStatus) {
                interfaceUpdateMask |= UPDATE_MASK_STATUS;
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateUserName) {
                interfaceUpdateMask |= UPDATE_MASK_NAME;
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateUserPhoto) {
                interfaceUpdateMask |= UPDATE_MASK_AVATAR;
                MessagesStorage.getInstance().clearUserPhotos(update.user_id);
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateContactRegistered) {
                if (enableJoined && usersDict.containsKey(update.user_id)) {
                    TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                    newMessage.action = new TLRPC.TL_messageActionUserJoined();
                    newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                    UserConfig.saveConfig(false);
                    newMessage.unread = true;
                    newMessage.date = update.date;
                    newMessage.from_id = update.user_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.getClientUserId();
                    newMessage.out = false;
                    newMessage.dialog_id = update.user_id;

                    messagesArr.add(newMessage);
                    MessageObject obj = new MessageObject(newMessage, usersDict);
                    ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                    if (arr == null) {
                        arr = new ArrayList<MessageObject>();
                        messages.put(newMessage.dialog_id, arr);
                    }
                    arr.add(obj);
                    pushMessages.add(obj);
                }
            } else if (update instanceof TLRPC.TL_updateContactLink) {
                if (update.my_link instanceof TLRPC.TL_contacts_myLinkContact || update.my_link instanceof TLRPC.TL_contacts_myLinkRequested && update.my_link.contact) {
                    int idx = contactsIds.indexOf(-update.user_id);
                    if (idx != -1) {
                        contactsIds.remove(idx);
                    }
                    if (!contactsIds.contains(update.user_id)) {
                        contactsIds.add(update.user_id);
                    }
                } else {
                    int idx = contactsIds.indexOf(update.user_id);
                    if (idx != -1) {
                        contactsIds.remove(idx);
                    }
                    if (!contactsIds.contains(update.user_id)) {
                        contactsIds.add(-update.user_id);
                    }
                }
            } else if (update instanceof TLRPC.TL_updateActivation) {
                //DEPRECATED
            } else if (update instanceof TLRPC.TL_updateNewAuthorization) {
                TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                newMessage.action = new TLRPC.TL_messageActionLoginUnknownLocation();
                newMessage.action.title = update.device;
                newMessage.action.address = update.location;
                newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                UserConfig.saveConfig(false);
                newMessage.unread = true;
                newMessage.date = update.date;
                newMessage.from_id = 333000;
                newMessage.to_id = new TLRPC.TL_peerUser();
                newMessage.to_id.user_id = UserConfig.getClientUserId();
                newMessage.out = false;
                newMessage.dialog_id = 333000;

                messagesArr.add(newMessage);
                MessageObject obj = new MessageObject(newMessage, usersDict);
                ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<MessageObject>();
                    messages.put(newMessage.dialog_id, arr);
                }
                arr.add(obj);
                pushMessages.add(obj);
            } else if (update instanceof TLRPC.TL_updateNewGeoChatMessage) {
                //DEPRECATED
            } else if (update instanceof TLRPC.TL_updateNewEncryptedMessage) {
                MessagesStorage.lastQtsValue = update.qts;
                TLRPC.Message message = decryptMessage(((TLRPC.TL_updateNewEncryptedMessage)update).message);
                if (message != null) {
                    int cid = ((TLRPC.TL_updateNewEncryptedMessage)update).message.chat_id;
                    messagesArr.add(message);
                    MessageObject obj = new MessageObject(message, usersDict);
                    long uid = ((long)cid) << 32;
                    ArrayList<MessageObject> arr = messages.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<MessageObject>();
                        messages.put(uid, arr);
                    }
                    arr.add(obj);
                    pushMessages.add(obj);
                }
            } else if (update instanceof TLRPC.TL_updateEncryptedChatTyping) {
                TLRPC.EncryptedChat encryptedChat = getEncryptedChat(update.chat_id);
                if (encryptedChat != null) {
                    update.user_id = encryptedChat.user_id;
                    long uid = ((long) update.chat_id) << 32;
                    ArrayList<PrintingUser> arr = printingUsers.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<PrintingUser>();
                        printingUsers.put(uid, arr);
                    }
                    boolean exist = false;
                    for (PrintingUser u : arr) {
                        if (u.userId == update.user_id) {
                            exist = true;
                            u.lastTime = currentTime;
                            break;
                        }
                    }
                    if (!exist) {
                        PrintingUser newUser = new PrintingUser();
                        newUser.userId = update.user_id;
                        newUser.lastTime = currentTime;
                        arr.add(newUser);
                        printChanged = true;
                    }
                }
            } else if (update instanceof TLRPC.TL_updateEncryptedMessagesRead) {
                markAsReadEncrypted.put(update.chat_id, Math.max(update.max_date, update.date));
                tasks.add((TLRPC.TL_updateEncryptedMessagesRead)update);
            } else if (update instanceof TLRPC.TL_updateChatParticipantAdd) {
                MessagesStorage.getInstance().updateChatInfo(update.chat_id, update.user_id, false, update.inviter_id, update.version);
            } else if (update instanceof TLRPC.TL_updateChatParticipantDelete) {
                MessagesStorage.getInstance().updateChatInfo(update.chat_id, update.user_id, true, 0, update.version);
            } else if (update instanceof TLRPC.TL_updateDcOptions) {
                ConnectionsManager.getInstance().updateDcSettings(0);
            } else if (update instanceof TLRPC.TL_updateEncryption) {
                final TLRPC.EncryptedChat newChat = update.chat;
                long dialog_id = ((long)newChat.id) << 32;
                TLRPC.EncryptedChat existingChat = getEncryptedChat(newChat.id);

                if (newChat instanceof TLRPC.TL_encryptedChatRequested && existingChat == null) {
                    int user_id = newChat.participant_id;
                    if (user_id == UserConfig.getClientUserId()) {
                        user_id = newChat.admin_id;
                    }
                    TLRPC.User user = users.get(user_id);
                    if (user == null) {
                        user = usersDict.get(user_id);
                    }
                    newChat.user_id = user_id;
                    final TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
                    dialog.id = dialog_id;
                    dialog.unread_count = 0;
                    dialog.top_message = 0;
                    dialog.last_message_date = update.date;

                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            dialogs_dict.put(dialog.id, dialog);
                            dialogs.add(dialog);
                            dialogsServerOnly.clear();
                            encryptedChats.put(newChat.id, newChat);
                            Collections.sort(dialogs, new Comparator<TLRPC.TL_dialog>() {
                                @Override
                                public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                                    if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                        return 0;
                                    } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                        return 1;
                                    } else {
                                        return -1;
                                    }
                                }
                            });
                            for (TLRPC.TL_dialog d : dialogs) {
                                if ((int)d.id != 0) {
                                    dialogsServerOnly.add(d);
                                }
                            }
                            NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                        }
                    });
                    MessagesStorage.getInstance().putEncryptedChat(newChat, user, dialog);
                    acceptSecretChat(newChat);
                } else if (newChat instanceof TLRPC.TL_encryptedChat) {
                    if (existingChat != null && existingChat instanceof TLRPC.TL_encryptedChatWaiting && (existingChat.auth_key == null || existingChat.auth_key.length == 1)) {
                        newChat.a_or_b = existingChat.a_or_b;
                        newChat.user_id = existingChat.user_id;
                        processAcceptedSecretChat(newChat);
                    } else if (existingChat == null && startingSecretChat) {
                        delayedEncryptedChatUpdates.add(update);
                    }
                } else {
                    final TLRPC.EncryptedChat exist = existingChat;
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (exist != null) {
                                newChat.user_id = exist.user_id;
                                newChat.auth_key = exist.auth_key;
                                newChat.ttl = exist.ttl;
                                encryptedChats.put(newChat.id, newChat);
                            }
                            MessagesStorage.getInstance().updateEncryptedChat(newChat);
                            NotificationCenter.getInstance().postNotificationName(encryptedChatUpdated, newChat);
                        }
                    });
                }
            } else if (update instanceof TLRPC.TL_updateUserBlocked) {
                //TODO
            } else if (update instanceof TLRPC.TL_updateNotifySettings) {
                updatesOnMainThread.add(update);
            }
        }
        if (!messages.isEmpty()) {
            for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : messages.entrySet()) {
                Long key = pair.getKey();
                ArrayList<MessageObject> value = pair.getValue();
                if (updatePrintingUsersWithNewMessages(key, value)) {
                    printChanged = true;
                }
            }
        }

        if (printChanged) {
            updatePrintingStrings();
        }

        final int interfaceUpdateMaskFinal = interfaceUpdateMask;
        final boolean printChangedArg = printChanged;

        processPendingEncMessages();

        if (!contactsIds.isEmpty()) {
            ContactsController.getInstance().processContactsUpdates(contactsIds, usersDict);
        }

        if (!messagesArr.isEmpty()) {
            MessagesStorage.getInstance().putMessages(messagesArr, true, true);
        }

        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                int updateMask = interfaceUpdateMaskFinal;

                boolean avatarsUpdate = false;
                if (!updatesOnMainThread.isEmpty()) {
                    ArrayList<TLRPC.User> dbUsers = new ArrayList<TLRPC.User>();
                    ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<TLRPC.User>();
                    SharedPreferences.Editor editor = null;
                    for (TLRPC.Update update : updatesOnMainThread) {
                        TLRPC.User toDbUser = new TLRPC.User();
                        toDbUser.id = update.user_id;
                        TLRPC.User currentUser = users.get(update.user_id);
                        if (update instanceof TLRPC.TL_updateUserStatus) {
                            if (currentUser != null) {
                                currentUser.id = update.user_id;
                                currentUser.status = update.status;
                            }
                            toDbUser.status = update.status;
                            dbUsersStatus.add(toDbUser);
                        } else if (update instanceof TLRPC.TL_updateUserName) {
                            if (currentUser != null) {
                                currentUser.first_name = update.first_name;
                                currentUser.last_name = update.last_name;
                            }
                            toDbUser.first_name = update.first_name;
                            toDbUser.last_name = update.last_name;
                            dbUsers.add(toDbUser);
                        } else if (update instanceof TLRPC.TL_updateUserPhoto) {
                            if (currentUser != null) {
                                currentUser.photo = update.photo;
                            }
                            avatarsUpdate = true;
                            toDbUser.photo = update.photo;
                            dbUsers.add(toDbUser);
                        } else if (update instanceof TLRPC.TL_updateNotifySettings) {
                            if (update.notify_settings instanceof TLRPC.TL_peerNotifySettings && update.peer instanceof TLRPC.TL_notifyPeer) {
                                if (editor == null) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    editor = preferences.edit();
                                }
                                int dialog_id = update.peer.peer.user_id;
                                if (dialog_id == 0) {
                                    dialog_id = -update.peer.peer.chat_id;
                                }
                                if (update.notify_settings.mute_until != 0) {
                                    editor.putInt("notify2_" + dialog_id, 2);
                                } else {
                                    editor.remove("notify2_" + dialog_id);
                                }
                            } else if (update.peer instanceof TLRPC.TL_notifyChats) {
                                if (editor == null) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    editor = preferences.edit();
                                }
                                editor.putBoolean("EnableGroup", update.notify_settings.mute_until == 0);
                                editor.putBoolean("EnablePreviewGroup", update.notify_settings.show_previews);
                            } else if (update.peer instanceof TLRPC.TL_notifyUsers) {
                                if (editor == null) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    editor = preferences.edit();
                                }
                                editor.putBoolean("EnableAll", update.notify_settings.mute_until == 0);
                                editor.putBoolean("EnablePreviewAll", update.notify_settings.show_previews);
                            }
                        }
                    }
                    if (editor != null) {
                        editor.commit();
                        NotificationCenter.getInstance().postNotificationName(notificationsSettingsUpdated);
                    }
                    MessagesStorage.getInstance().updateUsers(dbUsersStatus, true, true, true);
                    MessagesStorage.getInstance().updateUsers(dbUsers, false, true, true);
                }

                if (!messages.isEmpty()) {
                    for (HashMap.Entry<Long, ArrayList<MessageObject>> entry : messages.entrySet()) {
                        Long key = entry.getKey();
                        ArrayList<MessageObject> value = entry.getValue();
                        updateInterfaceWithMessages(key, value);
                    }
                    NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                }
                if (!pushMessages.isEmpty()) {
                    NotificationsController.getInstance().processNewMessages(pushMessages, true);
                }
                if (!markAsReadMessages.isEmpty()) {
                    for (Integer id : markAsReadMessages) {
                        MessageObject obj = dialogMessage.get(id);
                        if (obj != null) {
                            obj.messageOwner.unread = false;
                            updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                        }
                    }
                }
                if (!markAsReadEncrypted.isEmpty()) {
                    for (HashMap.Entry<Integer, Integer> entry : markAsReadEncrypted.entrySet()) {
                        NotificationCenter.getInstance().postNotificationName(messagesReadedEncrypted, entry.getKey(), entry.getValue());
                        long dialog_id = (long)(entry.getKey()) << 32;
                        TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
                        if (dialog != null) {
                            MessageObject message = dialogMessage.get(dialog.top_message);
                            if (message != null && message.messageOwner.date <= entry.getValue()) {
                                message.messageOwner.unread = false;
                                updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                            }
                        }
                    }
                }
                if (!markAsReadMessages.isEmpty()) {
                    NotificationsController.getInstance().processReadMessages(markAsReadMessages, 0, 0, 0);
                }
                if (!deletedMessages.isEmpty()) {
                    NotificationCenter.getInstance().postNotificationName(messagesDeleted, deletedMessages);
                    for (Integer id : deletedMessages) {
                        MessageObject obj = dialogMessage.get(id);
                        if (obj != null) {
                            obj.deleted = true;
                        }
                    }
                }
                if (printChangedArg) {
                    updateMask |= UPDATE_MASK_USER_PRINT;
                }
                if (!contactsIds.isEmpty()) {
                    updateMask |= UPDATE_MASK_NAME;
                    updateMask |= UPDATE_MASK_USER_PHONE;
                }
                if (!chatInfoToUpdate.isEmpty()) {
                    for (TLRPC.ChatParticipants info : chatInfoToUpdate) {
                        MessagesStorage.getInstance().updateChatInfo(info.chat_id, info, true);
                        NotificationCenter.getInstance().postNotificationName(chatInfoDidLoaded, info.chat_id, info);
                    }
                }
                if (updateMask != 0) {
                    NotificationCenter.getInstance().postNotificationName(updateInterfaces, updateMask);
                }
            }
        });

        if (!markAsReadMessages.isEmpty() || !markAsReadEncrypted.isEmpty()) {
            MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!markAsReadMessages.isEmpty()) {
                                NotificationCenter.getInstance().postNotificationName(messagesReaded, markAsReadMessages);
                            }
                        }
                    });
                }
            });
        }

        if (!markAsReadMessages.isEmpty() || !markAsReadEncrypted.isEmpty()) {
            if (!markAsReadMessages.isEmpty()) {
                MessagesStorage.getInstance().updateDialogsWithReadedMessages(markAsReadMessages, true);
            }
            MessagesStorage.getInstance().markMessagesAsRead(markAsReadMessages, markAsReadEncrypted, true);
        }
        if (!deletedMessages.isEmpty()) {
            MessagesStorage.getInstance().markMessagesAsDeleted(deletedMessages, true);
        }
        if (!deletedMessages.isEmpty()) {
            MessagesStorage.getInstance().updateDialogsWithDeletedMessages(deletedMessages, true);
        }
        if (!tasks.isEmpty()) {
            for (TLRPC.TL_updateEncryptedMessagesRead update : tasks) {
                MessagesStorage.getInstance().createTaskForDate(update.chat_id, update.max_date, update.date, 1);
            }
        }

        return true;
    }

    private boolean updatePrintingUsersWithNewMessages(long uid, ArrayList<MessageObject> messages) {
        if (uid > 0) {
            ArrayList<PrintingUser> arr = printingUsers.get(uid);
            if (arr != null) {
                printingUsers.remove(uid);
                return true;
            }
        } else if (uid < 0) {
            ArrayList<Integer> messagesUsers = new ArrayList<Integer>();
            for (MessageObject message : messages) {
                if (!messagesUsers.contains(message.messageOwner.from_id)) {
                    messagesUsers.add(message.messageOwner.from_id);
                }
            }

            ArrayList<PrintingUser> arr = printingUsers.get(uid);
            boolean changed = false;
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    PrintingUser user = arr.get(a);
                    if (messagesUsers.contains(user.userId)) {
                        arr.remove(a);
                        a--;
                        if (arr.isEmpty()) {
                            printingUsers.remove(uid);
                        }
                        changed = true;
                    }
                }
            }
            if (changed) {
                return true;
            }
        }
        return false;
    }

    public void dialogsUnreadCountIncr(final HashMap<Long, Integer> values) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                for (HashMap.Entry<Long, Integer> entry : values.entrySet()) {
                    TLRPC.TL_dialog dialog = dialogs_dict.get(entry.getKey());
                    if (dialog != null) {
                        dialog.unread_count += entry.getValue();
                    }
                }
                NotificationsController.getInstance().processDialogsUpdateRead(values, false);
                NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
            }
        });
    }

    private void updateInterfaceWithMessages(long uid, ArrayList<MessageObject> messages) {
        MessageObject lastMessage = null;
        TLRPC.TL_dialog dialog = dialogs_dict.get(uid);

        boolean isEncryptedChat = ((int)uid) == 0;

        NotificationCenter.getInstance().postNotificationName(didReceivedNewMessages, uid, messages);

        for (MessageObject message : messages) {
            if (lastMessage == null || (!isEncryptedChat && message.messageOwner.id > lastMessage.messageOwner.id || isEncryptedChat && message.messageOwner.id < lastMessage.messageOwner.id) || message.messageOwner.date > lastMessage.messageOwner.date) {
                lastMessage = message;
            }
        }

        boolean changed = false;

        if (dialog == null) {
            dialog = new TLRPC.TL_dialog();
            dialog.id = uid;
            dialog.unread_count = 0;
            dialog.top_message = lastMessage.messageOwner.id;
            dialog.last_message_date = lastMessage.messageOwner.date;
            dialogs_dict.put(uid, dialog);
            dialogs.add(dialog);
            dialogMessage.put(lastMessage.messageOwner.id, lastMessage);
            changed = true;
        } else {
            if (dialog.top_message > 0 && lastMessage.messageOwner.id > 0 && lastMessage.messageOwner.id > dialog.top_message ||
                    dialog.top_message < 0 && lastMessage.messageOwner.id < 0 && lastMessage.messageOwner.id < dialog.top_message ||
                    dialog.last_message_date < lastMessage.messageOwner.date) {
                dialogMessage.remove(dialog.top_message);
                dialog.top_message = lastMessage.messageOwner.id;
                dialog.last_message_date = lastMessage.messageOwner.date;
                dialogMessage.put(lastMessage.messageOwner.id, lastMessage);
                changed = true;
            }
        }

        if (changed) {
            dialogsServerOnly.clear();
            Collections.sort(dialogs, new Comparator<TLRPC.TL_dialog>() {
                @Override
                public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                    if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                        return 0;
                    } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            });
            for (TLRPC.TL_dialog d : dialogs) {
                if ((int)d.id != 0) {
                    dialogsServerOnly.add(d);
                }
            }
        }
    }

    public TLRPC.EncryptedChat getEncryptedChat(int chat_id) {
        TLRPC.EncryptedChat chat = encryptedChats.get(chat_id);
        if (chat == null) {
            Semaphore semaphore = new Semaphore(0);
            ArrayList<TLObject> result = new ArrayList<TLObject>();
            MessagesStorage.getInstance().getEncryptedChat(chat_id, semaphore, result);
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (result.size() == 2) {
                chat = (TLRPC.EncryptedChat)result.get(0);
                TLRPC.User user = (TLRPC.User)result.get(1);
                encryptedChats.put(chat.id, chat);
                users.putIfAbsent(user.id, user);
            }
        }
        return chat;
    }

    public TLRPC.Message decryptMessage(TLRPC.EncryptedMessage message) {
        TLRPC.EncryptedChat chat = getEncryptedChat(message.chat_id);
        if (chat == null) {
            return null;
        }
        ByteBufferDesc is = BuffersStorage.getInstance().getFreeBuffer(message.bytes.length);
        is.writeRaw(message.bytes);
        is.position(0);
        long fingerprint = is.readInt64();
        if (chat.key_fingerprint == fingerprint) {
            byte[] messageKey = is.readData(16);
            MessageKeyData keyData = Utilities.generateMessageKeyData(chat.auth_key, messageKey, false);

            Utilities.aesIgeEncryption(is.buffer, keyData.aesKey, keyData.aesIv, false, false, 24, is.limit() - 24);

            int len = is.readInt32();
            TLObject object = TLClassStore.Instance().TLdeserialize(is, is.readInt32());
            BuffersStorage.getInstance().reuseFreeBuffer(is);
            if (object != null) {

                int from_id = chat.admin_id;
                if (from_id == UserConfig.getClientUserId()) {
                    from_id = chat.participant_id;
                }

                if (object instanceof TLRPC.TL_decryptedMessage) {
                    TLRPC.TL_decryptedMessage decryptedMessage = (TLRPC.TL_decryptedMessage)object;
                    TLRPC.TL_message newMessage = new TLRPC.TL_message();
                    newMessage.message = decryptedMessage.message;
                    newMessage.date = message.date;
                    newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                    UserConfig.saveConfig(false);
                    newMessage.from_id = from_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.random_id = message.random_id;
                    newMessage.to_id.user_id = UserConfig.getClientUserId();
                    newMessage.out = false;
                    newMessage.unread = true;
                    newMessage.dialog_id = ((long)chat.id) << 32;
                    newMessage.ttl = chat.ttl;
                    if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaEmpty) {
                        newMessage.media = new TLRPC.TL_messageMediaEmpty();
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaContact) {
                        newMessage.media = new TLRPC.TL_messageMediaContact();
                        newMessage.media.last_name = decryptedMessage.media.last_name;
                        newMessage.media.first_name = decryptedMessage.media.first_name;
                        newMessage.media.phone_number = decryptedMessage.media.phone_number;
                        newMessage.media.user_id = decryptedMessage.media.user_id;
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaGeoPoint) {
                        newMessage.media = new TLRPC.TL_messageMediaGeo();
                        newMessage.media.geo = new TLRPC.TL_geoPoint();
                        newMessage.media.geo.lat = decryptedMessage.media.lat;
                        newMessage.media.geo._long = decryptedMessage.media._long;
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaPhoto) {
                        if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                            return null;
                        }
                        newMessage.media = new TLRPC.TL_messageMediaPhoto();
                        newMessage.media.photo = new TLRPC.TL_photo();
                        newMessage.media.photo.user_id = newMessage.from_id;
                        newMessage.media.photo.date = newMessage.date;
                        newMessage.media.photo.caption = "";
                        newMessage.media.photo.geo = new TLRPC.TL_geoPointEmpty();
                        if (decryptedMessage.media.thumb.length != 0 && decryptedMessage.media.thumb.length <= 5000 && decryptedMessage.media.thumb_w < 100 && decryptedMessage.media.thumb_h < 100) {
                            TLRPC.TL_photoCachedSize small = new TLRPC.TL_photoCachedSize();
                            small.w = decryptedMessage.media.thumb_w;
                            small.h = decryptedMessage.media.thumb_h;
                            small.bytes = decryptedMessage.media.thumb;
                            small.type = "s";
                            small.location = new TLRPC.TL_fileLocationUnavailable();
                            newMessage.media.photo.sizes.add(small);
                        }

                        TLRPC.TL_photoSize big = new TLRPC.TL_photoSize();
                        big.w = decryptedMessage.media.w;
                        big.h = decryptedMessage.media.h;
                        big.type = "x";
                        big.size = message.file.size;
                        big.location = new TLRPC.TL_fileEncryptedLocation();
                        big.location.key = decryptedMessage.media.key;
                        big.location.iv = decryptedMessage.media.iv;
                        big.location.dc_id = message.file.dc_id;
                        big.location.volume_id = message.file.id;
                        big.location.secret = message.file.access_hash;
                        big.location.local_id = message.file.key_fingerprint;
                        newMessage.media.photo.sizes.add(big);
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaVideo) {
                        if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                            return null;
                        }
                        newMessage.media = new TLRPC.TL_messageMediaVideo();
                        newMessage.media.video = new TLRPC.TL_videoEncrypted();
                        if (decryptedMessage.media.thumb.length != 0 && decryptedMessage.media.thumb.length <= 5000 && decryptedMessage.media.thumb_w < 100 && decryptedMessage.media.thumb_h < 100) {
                            newMessage.media.video.thumb = new TLRPC.TL_photoCachedSize();
                            newMessage.media.video.thumb.bytes = decryptedMessage.media.thumb;
                            newMessage.media.video.thumb.w = decryptedMessage.media.thumb_w;
                            newMessage.media.video.thumb.h = decryptedMessage.media.thumb_h;
                            newMessage.media.video.thumb.type = "s";
                            newMessage.media.video.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                        } else {
                            newMessage.media.video.thumb = new TLRPC.TL_photoSizeEmpty();
                            newMessage.media.video.thumb.type = "s";
                        }
                        newMessage.media.video.duration = decryptedMessage.media.duration;
                        newMessage.media.video.dc_id = message.file.dc_id;
                        newMessage.media.video.w = decryptedMessage.media.w;
                        newMessage.media.video.h = decryptedMessage.media.h;
                        newMessage.media.video.date = message.date;
                        newMessage.media.video.caption = "";
                        newMessage.media.video.user_id = from_id;
                        newMessage.media.video.size = message.file.size;
                        newMessage.media.video.id = message.file.id;
                        newMessage.media.video.access_hash = message.file.access_hash;
                        newMessage.media.video.key = decryptedMessage.media.key;
                        newMessage.media.video.iv = decryptedMessage.media.iv;
                        newMessage.media.video.mime_type = decryptedMessage.media.mime_type;
                        if (newMessage.media.video.mime_type == null) {
                            newMessage.media.video.mime_type = "video/mp4";
                        }
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaDocument) {
                        if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                            return null;
                        }
                        newMessage.media = new TLRPC.TL_messageMediaDocument();
                        newMessage.media.document = new TLRPC.TL_documentEncrypted();
                        newMessage.media.document.id = message.file.id;
                        newMessage.media.document.access_hash = message.file.access_hash;
                        newMessage.media.document.user_id = decryptedMessage.media.user_id;
                        newMessage.media.document.date = message.date;
                        newMessage.media.document.file_name = decryptedMessage.media.file_name;
                        newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                        newMessage.media.document.size = message.file.size;
                        newMessage.media.document.key = decryptedMessage.media.key;
                        newMessage.media.document.iv = decryptedMessage.media.iv;
                        if (decryptedMessage.media.thumb.length != 0 && decryptedMessage.media.thumb.length <= 5000 && decryptedMessage.media.thumb_w < 100 && decryptedMessage.media.thumb_h < 100) {
                            newMessage.media.document.thumb = new TLRPC.TL_photoCachedSize();
                            newMessage.media.document.thumb.bytes = decryptedMessage.media.thumb;
                            newMessage.media.document.thumb.w = decryptedMessage.media.thumb_w;
                            newMessage.media.document.thumb.h = decryptedMessage.media.thumb_h;
                            newMessage.media.document.thumb.type = "s";
                            newMessage.media.document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                        } else {
                            newMessage.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
                            newMessage.media.document.thumb.type = "s";
                        }
                        newMessage.media.document.dc_id = message.file.dc_id;
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaAudio) {
                        if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                            return null;
                        }
                        newMessage.media = new TLRPC.TL_messageMediaAudio();
                        newMessage.media.audio = new TLRPC.TL_audioEncrypted();
                        newMessage.media.audio.id = message.file.id;
                        newMessage.media.audio.access_hash = message.file.access_hash;
                        newMessage.media.audio.user_id = from_id;
                        newMessage.media.audio.date = message.date;
                        newMessage.media.audio.size = message.file.size;
                        newMessage.media.audio.key = decryptedMessage.media.key;
                        newMessage.media.audio.iv = decryptedMessage.media.iv;
                        newMessage.media.audio.dc_id = message.file.dc_id;
                        newMessage.media.audio.duration = decryptedMessage.media.duration;
                        newMessage.media.audio.mime_type = decryptedMessage.media.mime_type;
                        if (newMessage.media.audio.mime_type == null) {
                            newMessage.media.audio.mime_type = "audio/ogg";
                        }
                    } else {
                        return null;
                    }
                    return newMessage;
                } else if (object instanceof TLRPC.TL_decryptedMessageService) {
                    TLRPC.TL_decryptedMessageService serviceMessage = (TLRPC.TL_decryptedMessageService)object;
                    if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL || serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                        TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                        if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                            newMessage.action = new TLRPC.TL_messageActionTTLChange();
                            newMessage.action.ttl = chat.ttl = serviceMessage.action.ttl_seconds;
                        } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                            newMessage.action = new TLRPC.TL_messageEcryptedAction();
                            newMessage.action.encryptedAction = serviceMessage.action;
                        } else {
                            return null;
                        }
                        newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                        UserConfig.saveConfig(false);
                        newMessage.unread = true;
                        newMessage.date = message.date;
                        newMessage.from_id = from_id;
                        newMessage.to_id = new TLRPC.TL_peerUser();
                        newMessage.to_id.user_id = UserConfig.getClientUserId();
                        newMessage.out = false;
                        newMessage.dialog_id = ((long)chat.id) << 32;
                        MessagesStorage.getInstance().updateEncryptedChatTTL(chat);
                        return newMessage;
                    } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionFlushHistory) {
                        final long did = ((long)chat.id) << 32;
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                TLRPC.TL_dialog dialog = dialogs_dict.get(did);
                                if (dialog != null) {
                                    dialogMessage.remove(dialog.top_message);
                                }
                                MessagesStorage.getInstance().deleteDialog(did, true);
                                NotificationCenter.getInstance().postNotificationName(removeAllMessagesFromDialog, did);
                                NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                            }
                        });
                        return null;
                    } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionDeleteMessages) {
                        if (!serviceMessage.action.random_ids.isEmpty()) {
                            pendingEncMessagesToDelete.addAll(serviceMessage.action.random_ids);
                        }
                        return null;
                    }
                } else {
                    FileLog.e("tmessages", "unkown message " + object);
                }
            } else {
                FileLog.e("tmessages", "unkown TLObject");
            }
        } else {
            FileLog.e("tmessages", "fingerprint mismatch");
        }
        BuffersStorage.getInstance().reuseFreeBuffer(is);
        return null;
    }

    public void processAcceptedSecretChat(final TLRPC.EncryptedChat encryptedChat) {
        BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);
        BigInteger i_authKey = new BigInteger(1, encryptedChat.g_a_or_b);

        if (!Utilities.isGoodGaAndGb(i_authKey, p)) {
            declineSecretChat(encryptedChat.id);
            return;
        }

        i_authKey = i_authKey.modPow(new BigInteger(1, encryptedChat.a_or_b), p);

        byte[] authKey = i_authKey.toByteArray();
        if (authKey.length > 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
            authKey = correctedAuth;
        } else if (authKey.length < 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
            for (int a = 0; a < 256 - authKey.length; a++) {
                authKey[a] = 0;
            }
            authKey = correctedAuth;
        }
        byte[] authKeyHash = Utilities.computeSHA1(authKey);
        byte[] authKeyId = new byte[8];
        System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
        long fingerprint = Utilities.bytesToLong(authKeyId);
        if (encryptedChat.key_fingerprint == fingerprint) {
            encryptedChat.auth_key = authKey;
            MessagesStorage.getInstance().updateEncryptedChat(encryptedChat);
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    encryptedChats.put(encryptedChat.id, encryptedChat);
                    NotificationCenter.getInstance().postNotificationName(encryptedChatUpdated, encryptedChat);
                }
            });
        } else {
            final TLRPC.TL_encryptedChatDiscarded newChat = new TLRPC.TL_encryptedChatDiscarded();
            newChat.id = encryptedChat.id;
            newChat.user_id = encryptedChat.user_id;
            newChat.auth_key = encryptedChat.auth_key;
            MessagesStorage.getInstance().updateEncryptedChat(newChat);
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    encryptedChats.put(newChat.id, newChat);
                    NotificationCenter.getInstance().postNotificationName(encryptedChatUpdated, newChat);
                }
            });
            declineSecretChat(encryptedChat.id);
        }
    }

    public void declineSecretChat(int chat_id) {
        TLRPC.TL_messages_discardEncryption req = new TLRPC.TL_messages_discardEncryption();
        req.chat_id = chat_id;
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public void acceptSecretChat(final TLRPC.EncryptedChat encryptedChat) {
        if (acceptingChats.get(encryptedChat.id) != null) {
            return;
        }
        acceptingChats.put(encryptedChat.id, encryptedChat);
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = MessagesStorage.lastSecretVersion;
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
                    if (response instanceof TLRPC.TL_messages_dhConfig) {
                        if (!Utilities.isGoodPrime(res.p, res.g)) {
                            acceptingChats.remove(encryptedChat.id);
                            declineSecretChat(encryptedChat.id);
                            return;
                        }

                        MessagesStorage.secretPBytes = res.p;
                        MessagesStorage.secretG = res.g;
                        MessagesStorage.lastSecretVersion = res.version;
                        MessagesStorage.getInstance().saveSecretParams(MessagesStorage.lastSecretVersion, MessagesStorage.secretG, MessagesStorage.secretPBytes);
                    }
                    byte[] salt = new byte[256];
                    for (int a = 0; a < 256; a++) {
                        salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
                    }
                    encryptedChat.a_or_b = salt;
                    BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);
                    BigInteger g_b = BigInteger.valueOf(MessagesStorage.secretG);
                    g_b = g_b.modPow(new BigInteger(1, salt), p);
                    BigInteger g_a = new BigInteger(1, encryptedChat.g_a);

                    if (!Utilities.isGoodGaAndGb(g_a, p)) {
                        acceptingChats.remove(encryptedChat.id);
                        declineSecretChat(encryptedChat.id);
                        return;
                    }

                    byte[] g_b_bytes = g_b.toByteArray();
                    if (g_b_bytes.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(g_b_bytes, 1, correctedAuth, 0, 256);
                        g_b_bytes = correctedAuth;
                    }

                    g_a = g_a.modPow(new BigInteger(1, salt), p);

                    byte[] authKey = g_a.toByteArray();
                    if (authKey.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
                        authKey = correctedAuth;
                    } else if (authKey.length < 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
                        for (int a = 0; a < 256 - authKey.length; a++) {
                            authKey[a] = 0;
                        }
                        authKey = correctedAuth;
                    }
                    byte[] authKeyHash = Utilities.computeSHA1(authKey);
                    byte[] authKeyId = new byte[8];
                    System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
                    encryptedChat.auth_key = authKey;

                    TLRPC.TL_messages_acceptEncryption req2 = new TLRPC.TL_messages_acceptEncryption();
                    req2.g_b = g_b_bytes;
                    req2.peer = new TLRPC.TL_inputEncryptedChat();
                    req2.peer.chat_id = encryptedChat.id;
                    req2.peer.access_hash = encryptedChat.access_hash;
                    req2.key_fingerprint = Utilities.bytesToLong(authKeyId);
                    ConnectionsManager.getInstance().performRpc(req2, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            acceptingChats.remove(encryptedChat.id);
                            if (error == null) {
                                final TLRPC.EncryptedChat newChat = (TLRPC.EncryptedChat) response;
                                newChat.auth_key = encryptedChat.auth_key;
                                newChat.user_id = encryptedChat.user_id;
                                MessagesStorage.getInstance().updateEncryptedChat(newChat);
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        encryptedChats.put(newChat.id, newChat);
                                        NotificationCenter.getInstance().postNotificationName(encryptedChatUpdated, newChat);
                                    }
                                });
                            }
                        }
                    });
                } else {
                    acceptingChats.remove(encryptedChat.id);
                }
            }
        });
    }

    public void startSecretChat(final Context context, final TLRPC.User user) {
        if (user == null) {
            return;
        }
        startingSecretChat = true;
        final ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = MessagesStorage.lastSecretVersion;
        final long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
                    if (response instanceof TLRPC.TL_messages_dhConfig) {
                        if (!Utilities.isGoodPrime(res.p, res.g)) {
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (!((Activity) context).isFinishing()) {
                                            progressDialog.dismiss();
                                        }
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            });
                            return;
                        }
                        MessagesStorage.secretPBytes = res.p;
                        MessagesStorage.secretG = res.g;
                        MessagesStorage.lastSecretVersion = res.version;
                        MessagesStorage.getInstance().saveSecretParams(MessagesStorage.lastSecretVersion, MessagesStorage.secretG, MessagesStorage.secretPBytes);
                    }
                    final byte[] salt = new byte[256];
                    for (int a = 0; a < 256; a++) {
                        salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
                    }

                    BigInteger i_g_a = BigInteger.valueOf(MessagesStorage.secretG);
                    i_g_a = i_g_a.modPow(new BigInteger(1, salt), new BigInteger(1, MessagesStorage.secretPBytes));
                    byte[] g_a = i_g_a.toByteArray();
                    if (g_a.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(g_a, 1, correctedAuth, 0, 256);
                        g_a = correctedAuth;
                    }

                    TLRPC.TL_messages_requestEncryption req2 = new TLRPC.TL_messages_requestEncryption();
                    req2.g_a = g_a;
                    req2.user_id = getInputUser(user);
                    req2.random_id = Utilities.random.nextInt();
                    ConnectionsManager.getInstance().performRpc(req2, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(final TLObject response, TLRPC.TL_error error) {
                            if (error == null) {
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        startingSecretChat = false;
                                        if (!((Activity) context).isFinishing()) {
                                            try {
                                                progressDialog.dismiss();
                                            } catch (Exception e) {
                                                FileLog.e("tmessages", e);
                                            }
                                        }
                                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) response;
                                        chat.user_id = chat.participant_id;
                                        encryptedChats.put(chat.id, chat);
                                        chat.a_or_b = salt;
                                        TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
                                        dialog.id = ((long) chat.id) << 32;
                                        dialog.unread_count = 0;
                                        dialog.top_message = 0;
                                        dialog.last_message_date = ConnectionsManager.getInstance().getCurrentTime();
                                        dialogs_dict.put(dialog.id, dialog);
                                        dialogs.add(dialog);
                                        dialogsServerOnly.clear();
                                        Collections.sort(dialogs, new Comparator<TLRPC.TL_dialog>() {
                                            @Override
                                            public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                                                if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                                    return 0;
                                                } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                                    return 1;
                                                } else {
                                                    return -1;
                                                }
                                            }
                                        });
                                        for (TLRPC.TL_dialog d : dialogs) {
                                            if ((int) d.id != 0) {
                                                dialogsServerOnly.add(d);
                                            }
                                        }
                                        MessagesStorage.getInstance().putEncryptedChat(chat, user, dialog);
                                        NotificationCenter.getInstance().postNotificationName(dialogsNeedReload);
                                        NotificationCenter.getInstance().postNotificationName(encryptedChatCreated, chat);
                                        Utilities.stageQueue.postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!delayedEncryptedChatUpdates.isEmpty()) {
                                                    processUpdateArray(delayedEncryptedChatUpdates, null, null);
                                                    delayedEncryptedChatUpdates.clear();
                                                }
                                            }
                                        });
                                    }
                                });
                            } else {
                                delayedEncryptedChatUpdates.clear();
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!((Activity) context).isFinishing()) {
                                            startingSecretChat = false;
                                            try {
                                                progressDialog.dismiss();
                                            } catch (Exception e) {
                                                FileLog.e("tmessages", e);
                                            }
                                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                            builder.setMessage(LocaleController.formatString("CreateEncryptedChatOutdatedError", R.string.CreateEncryptedChatOutdatedError, user.first_name, user.first_name));
                                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                            builder.show().setCanceledOnTouchOutside(true);
                                        }
                                    }
                                });
                            }
                        }
                    }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
                } else {
                    delayedEncryptedChatUpdates.clear();
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            startingSecretChat = false;
                            if (!((Activity) context).isFinishing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                    });
                }
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ConnectionsManager.getInstance().cancelRpc(reqId, true);
                try {
                    dialog.dismiss();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
        progressDialog.show();
    }
}

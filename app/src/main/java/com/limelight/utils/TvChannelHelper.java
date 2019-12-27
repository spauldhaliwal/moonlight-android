package com.limelight.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import androidx.tvprovider.media.tv.TvContractCompat;
import androidx.tvprovider.media.tv.TvContractCompat.PreviewProgramColumns;
import androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms;
import androidx.tvprovider.media.tv.WatchNextProgram;
import com.limelight.LimeLog;
import com.limelight.PosterContentProvider;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class TvChannelHelper {

    private static final int ASPECT_RATIO_MOVIE_POSTER = 5;

    private static final int TYPE_GAME = 12;

    private static final int INTERNAL_PROVIDER_ID_INDEX = 1;

    private static final int PROGRAM_BROWSABLE_INDEX = 2;

    private static final String[] WATCH_NEXT_MAP_PROJECTION =
            {BaseColumns._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE};

    private static final int ID_INDEX = 0;

    private Activity context;

    public TvChannelHelper(Activity context) {
        this.context = context;
    }

    void requestChannelOnHomeScreen(ComputerDetails computer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isAndroidTV()) {
                return;
            }

            Long channelId = getChannelId(computer.uuid);
            if (channelId == null) {
                return;
            }

            Intent intent = new Intent(TvContract.ACTION_REQUEST_CHANNEL_BROWSABLE);
            intent.putExtra(TvContract.EXTRA_CHANNEL_ID, getChannelId(computer.uuid));
            try {
                context.startActivityForResult(intent, 0);
            } catch (ActivityNotFoundException e) {
            }
        }
    }

    void createTvChannel(ComputerDetails computer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isAndroidTV()) {
                return;
            }

            ChannelBuilder builder = new ChannelBuilder()
                    .setType(TvContract.Channels.TYPE_PREVIEW)
                    .setDisplayName("Your Games")
                    .setInternalProviderId(computer.uuid)
                    .setAppLinkIntent(ServerHelper.createPcShortcutIntent(context, computer));

            Long channelId = getChannelId(computer.uuid);
            if (channelId != null) {
                context.getContentResolver().update(TvContract.buildChannelUri(channelId),
                        builder.toContentValues(), null, null);
                return;
            }

            Uri channelUri = context.getContentResolver().insert(
                    TvContract.Channels.CONTENT_URI, builder.toContentValues());
            if (channelUri != null) {
                long id = ContentUris.parseId(channelUri);
                updateChannelIcon(id);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void updateChannelIcon(long channelId) {
        Bitmap logo = drawableToBitmap(context.getResources().getDrawable(R.drawable.ic_channel));
        try {
            Uri localUri = TvContract.buildChannelLogoUri(channelId);
            try (OutputStream outputStream = context.getContentResolver().openOutputStream(localUri)) {
                logo.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            } catch (SQLiteException | IOException e) {
                LimeLog.warning("Failed to store the logo to the system content provider.");
                e.printStackTrace();
            }
        } finally {
            logo.recycle();
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        int width = context.getResources().getDimensionPixelSize(R.dimen.tv_channel_logo_width);
        int height = context.getResources().getDimensionPixelSize(R.dimen.tv_channel_logo_width);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    void addGameToChannel(ComputerDetails computer, NvApp app) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isAndroidTV()) {
                return;
            }

            Long channelId = getChannelId(computer.uuid);
            if (channelId == null) {
                return;
            }

            PreviewProgramBuilder builder = new PreviewProgramBuilder()
                    .setChannelId(channelId)
                    .setType(TYPE_GAME)
                    .setTitle(app.getAppName())
                    .setDescription(app.getDescription())
                    .setPosterArtAspectRatio(ASPECT_RATIO_MOVIE_POSTER)
                    .setPosterArtUri(PosterContentProvider.createBoxArtUri(computer.uuid, "" + app.getAppId()))
                    .setIntent(ServerHelper.createAppShortcutIntent(context, computer, app))
                    .setInternalProviderId("" + app.getAppId());

            context.getContentResolver().insert(TvContract.PreviewPrograms.CONTENT_URI,
                    builder.toContentValues());

            TvContract.requestChannelBrowsable(context, channelId);
        }
    }

    void addGamesToChannel(ComputerDetails computer, List<NvApp> apps) {
        for (NvApp app : apps) {
            addGameToChannel(computer, app);
        }
    }

    void removeGameFromChannel(ComputerDetails computer, NvApp app) {
        Long channelId = getChannelId(computer.uuid);
        if (channelId == null) {
            return;
        }
        Long programId = getProgramId(channelId, "" + app.getAppId());
        if (programId != null) {
            context.getContentResolver()
                    .delete(TvContractCompat.buildPreviewProgramUri(programId), null, null);
        }
    }

    void removeGamesFromChannel(ComputerDetails computer, List<NvApp> apps) {
        for (NvApp app : apps) {
            removeGameFromChannel(computer, app);
        }

    }

    void addGameToWatchNext(ComputerDetails computer, NvApp app) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isAndroidTV()) {
                return;
            }

            Long channelId = getChannelId(computer.uuid);
            if (channelId == null) {
                return;
            }

            WatchNextProgram.Builder builder = new WatchNextProgram.Builder();
            builder.setType(WatchNextPrograms.TYPE_GAME)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                    .setTitle(app.getAppName())
                    .setPosterArtAspectRatio(PreviewProgramColumns.ASPECT_RATIO_MOVIE_POSTER)
                    .setPosterArtUri(PosterContentProvider.createBoxArtUri(computer.uuid, "" + app.getAppId()))
                    .setIntent(ServerHelper.createAppShortcutIntent(context, computer, app))
                    .setInternalProviderId("" + app.getAppId());

            ContentValues contentValues = builder.build().toContentValues();
            Long watchNextId = getWatchNextProgramId(app.getAppId());

            if (watchNextId != null) {
                int updatedRowCount = context.getContentResolver()
                        .update(TvContractCompat.buildWatchNextProgramUri(watchNextId),
                                contentValues,
                                null,
                                null);

                if (updatedRowCount < 1) {
                    context.getContentResolver()
                            .insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, contentValues);
                }
            } else {
                context.getContentResolver()
                        .insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, contentValues);
            }
        }
    }

    void deleteChannel(ComputerDetails computer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isAndroidTV()) {
                return;
            }

            Long channelId = getChannelId(computer.uuid);
            if (channelId == null) {
                return;
            }

            context.getContentResolver().delete(TvContract.buildChannelUri(channelId), null, null);
        }
    }

    void deleteProgram(ComputerDetails computer, NvApp app) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isAndroidTV()) {
                return;
            }

            Long channelId = getChannelId(computer.uuid);
            if (channelId == null) {
                return;
            }

            Long programId = getProgramId(channelId, "" + app.getAppId());
            if (programId == null) {
                return;
            }

            context.getContentResolver().delete(TvContract.buildPreviewProgramUri(programId), null, null);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private Long getChannelId(String computerUuid) {
        try (Cursor cursor = context.getContentResolver().query(
                TvContract.Channels.CONTENT_URI,
                new String[]{TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID},
                null,
                null,
                null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            while (cursor.moveToNext()) {
                String internalProviderId = cursor.getString(INTERNAL_PROVIDER_ID_INDEX);
                if (computerUuid.equals(internalProviderId)) {
                    return cursor.getLong(ID_INDEX);
                }
            }

            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private Long getProgramId(long channelId, String appId) {
        try (Cursor cursor = context.getContentResolver().query(
                TvContract.buildPreviewProgramsUriForChannel(channelId),
                new String[]{TvContract.PreviewPrograms._ID, TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                        TvContract.PreviewPrograms.COLUMN_BROWSABLE},
                null,
                null,
                null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            while (cursor.moveToNext()) {
                String internalProviderId = cursor.getString(INTERNAL_PROVIDER_ID_INDEX);
                if (appId.equals(internalProviderId)) {
                    long id = cursor.getLong(ID_INDEX);
                    int browsable = cursor.getInt(PROGRAM_BROWSABLE_INDEX);
                    if (browsable != 0) {
                        return id;
                    } else {
                        int countDeleted = context.getContentResolver()
                                .delete(TvContract.buildPreviewProgramUri(id), null, null);
                        if (countDeleted > 0) {
                            LimeLog.info("Preview program has been deleted");
                        } else {
                            LimeLog.warning("Preview program has not been deleted");
                        }
                    }
                }
            }

            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private Long getWatchNextProgramId(int appId) {
        try (Cursor cursor = context.getContentResolver().query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
                null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            while (cursor.moveToNext()) {
                String internalProviderId = cursor.getString(INTERNAL_PROVIDER_ID_INDEX);
                String appIdStr = Integer.toString(appId);
                if (appIdStr.equals(internalProviderId)) {
                    long id = cursor.getLong(ID_INDEX);
                    int browsable = cursor.getInt(PROGRAM_BROWSABLE_INDEX);
                    if (browsable != 0) {
                        return id;
                    } else {
                        int countDeleted = context.getContentResolver()
                                .delete(TvContract.buildPreviewProgramUri(id), null, null);
                        if (countDeleted > 0) {
                            LimeLog.info("Preview program has been deleted");
                        } else {
                            LimeLog.warning("Preview program has not been deleted");
                        }
                    }
                }
            }

            return null;
        }
    }

    private static <T> String toValueString(T value) {
        return value == null ? null : value.toString();
    }

    private static String toUriString(Intent intent) {
        return intent == null ? null : intent.toUri(Intent.URI_INTENT_SCHEME);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private boolean isAndroidTV() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private static class PreviewProgramBuilder {

        private ContentValues mValues = new ContentValues();


        public PreviewProgramBuilder setChannelId(Long channelId) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId);
            return this;
        }

        public PreviewProgramBuilder setType(int type) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_TYPE, type);
            return this;
        }

        public PreviewProgramBuilder setTitle(String title) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_TITLE, title);
            return this;
        }
        public PreviewProgramBuilder setDescription(String description) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, description);
            return this;
        }

        public PreviewProgramBuilder setPosterArtAspectRatio(int aspectRatio) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, aspectRatio);
            return this;
        }

        public PreviewProgramBuilder setIntent(Intent intent) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, toUriString(intent));
            return this;
        }

        public PreviewProgramBuilder setIntentUri(Uri uri) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, toValueString(uri));
            return this;
        }

        public PreviewProgramBuilder setInternalProviderId(String id) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, id);
            return this;
        }

        public PreviewProgramBuilder setPosterArtUri(Uri uri) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, toValueString(uri));
            return this;
        }

        public PreviewProgramBuilder setWeight(int weight) {
            mValues.put(TvContract.PreviewPrograms.COLUMN_WEIGHT, weight);
            return this;
        }

        public ContentValues toContentValues() {
            return new ContentValues(mValues);
        }

    }

    @TargetApi(Build.VERSION_CODES.O)
    private static class ChannelBuilder {

        private ContentValues mValues = new ContentValues();

        public ChannelBuilder setType(String type) {
            mValues.put(TvContract.Channels.COLUMN_TYPE, type);
            return this;
        }

        public ChannelBuilder setDisplayName(String displayName) {
            mValues.put(TvContract.Channels.COLUMN_DISPLAY_NAME, displayName);
            return this;
        }

        public ChannelBuilder setInternalProviderId(String internalProviderId) {
            mValues.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, internalProviderId);
            return this;
        }

        public ChannelBuilder setAppLinkIntent(Intent intent) {
            mValues.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, toUriString(intent));
            return this;
        }

        public ContentValues toContentValues() {
            return new ContentValues(mValues);
        }

    }
}

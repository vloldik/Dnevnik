package ru.vladik.dnevnik.Adapters;

import android.content.Context;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import ru.vladik.dnevnik.DiaryAPI.DataClasses.Reactions;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.FeedPost;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.FeedPostContent;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.webApi.Reaction;
import ru.vladik.dnevnik.DiaryAPI.DiaryAPI;
import ru.vladik.dnevnik.R;
import ru.vladik.dnevnik.Utils.AssetsHelper;
import ru.vladik.dnevnik.Utils.AsyncUtil;
import ru.vladik.dnevnik.Utils.DateHelper;
import ru.vladik.dnevnik.Utils.DiarySingleton;
import ru.vladik.dnevnik.Utils.DrawableHelper;
import ru.vladik.dnevnik.Utils.LayoutHelper;
import ru.vladik.dnevnik.Utils.NetworkHelper;

public class NewsAdapter extends ArrayAdapter<FeedPost> {

    private final int resourceLayout;
    private final DiaryAPI diaryAPI;

    public NewsAdapter(@NonNull Context context, int resource) {
        super(context, resource);
        diaryAPI = DiarySingleton.getInstance(getContext()).getDiaryAPI();
        resourceLayout = resource;
    }

    public void refreshList(List<FeedPost> feedPostList) {
        clear();
        addAll(feedPostList);
        NetworkHelper.startAsyncTaskCatchingApiErrors(getContext(), () -> {
            boolean refresh = false;
            for (FeedPost feedPost : feedPostList) {
                FeedPostContent content = feedPost.getContent();
                if (feedPost.getLogoDrawable() == null) {
                    refresh = true;
                    try {
                        if (content.getTopicLogoUrl() != null) {
                            feedPost.setLogoDrawable(DrawableHelper.drawableFromUrl(content.getTopicLogoUrl()));
                        }

                    } catch (IOException e) {
                        TypedValue typedValue = new TypedValue();
                        getContext().getTheme().resolveAttribute(R.attr.colorOnPrimary, typedValue, false);
                        feedPost.setLogoDrawable(new ColorDrawable(typedValue.data));
                    }

                }
            }
            if (refresh) {
                AsyncUtil.executeInMain(this::notifyDataSetChanged);
            }
        });
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            view = inflater.inflate(resourceLayout, null);
        }

        FeedPost feedPost = getItem(position);
        FeedPostContent content = feedPost.getContent();

        TextView authorTextView = view.findViewById(R.id.author_in_wall_post_item);
        TextView contentTextView = view.findViewById(R.id.content_in_wall_post_item);
        TextView userReactionTextView = view.findViewById(R.id.user_emoji_text_in_wall_post_item);
        ImageView userReactionImageView = view.findViewById(R.id.user_emoji_image_in_wall_post_item);
        ViewGroup userReactionLayout = view.findViewById(R.id.user_emoji_in_wall_post_item);
        TextView commentsTextView = view.findViewById(R.id.comments_text_view_in_wall_post_item);
        TextView dateTextView = view.findViewById(R.id.date_in_wall_post_item);
        LinearLayout reactionsCountLayout = view.findViewById(R.id.emoji_count_in_wall_post_item);
        ImageView avatarImageView = view.findViewById(R.id.avatar_in_wall_post_item);

        List<Reaction> reactions = content.getLikes().getVotes().getReactionsListWithoutEmpty();

        Drawable[] drawables = AssetsHelper.getDrawablesPack(AssetsHelper.OBAMA_PACK_PATH, getContext());

        reactionsCountLayout.removeAllViews();
        for (Reaction reaction : reactions) {
            Drawable drawable = drawables[Reactions.getEmotionId(reaction.getEmoji().getName())];
            ImageView imageView = new ImageView(getContext());
            imageView.setImageDrawable(drawable);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (drawable instanceof AnimatedImageDrawable && !((AnimatedImageDrawable) drawable).isRunning()) {
                    ((AnimatedImageDrawable) drawable).start();
                }
            }
            imageView.setPadding(5, 0, 0, 0);
            reactionsCountLayout.addView(imageView);
        }
        Integer countWithoutUser = content.getLikes().getCountWithoutUser();
        TextView reactionCountDescriptionTextView = new TextView(getContext());
        if (countWithoutUser != null && countWithoutUser != 0) {
            String text;
            reactionsCountLayout.setVisibility(View.VISIBLE);
            if (content.getLikes().getUserVoteId() != -1) {
                text = "Вы и еще " + countWithoutUser;
            } else {
                text = countWithoutUser.toString();
            }
            reactionCountDescriptionTextView.setText(text);
            reactionCountDescriptionTextView.setPadding(5, 0, 0, 0);
            reactionCountDescriptionTextView.setTextSize(17);
            reactionsCountLayout.addView(reactionCountDescriptionTextView);
            reactionCountDescriptionTextView.setGravity(Gravity.BOTTOM);
        } else {
            reactionsCountLayout.setVisibility(View.INVISIBLE);
        }


        View finalView = view;
        int i = content.getLikes().getUserVoteId();
        if (i > 0 && i < drawables.length) {
            userReactionImageView.setImageDrawable(drawables[i]);
            userReactionTextView.setText(Reactions.getReactionText(getContext(), i));
            userReactionLayout.setOnLongClickListener((v) ->
                    setAllReactionsDialog((ViewGroup) finalView,
                            content, content.getEventKey(), drawables));
            userReactionLayout.setOnClickListener((v) -> removeReaction(content, content.getEventKey()));
        } else if (i == -1) {
            userReactionImageView.setImageDrawable(drawables[0]);
            userReactionLayout.setOnClickListener((v) ->
                    setAllReactionsDialog((ViewGroup) finalView,
                            content, content.getEventKey(), drawables));
            userReactionTextView.setText(Reactions.getReactionText(getContext(), 0));
        }





        StringBuilder contentTextBuilder = new StringBuilder(content.getText());

        if (content.getAttachmentFiles() != null && !content.getAttachmentFiles().isEmpty()) {
            contentTextBuilder.append("\n");
            for (Object file : content.getAttachmentFiles()) {
                contentTextBuilder.append(file.toString());
            }
        }

        String contentText = contentTextBuilder.toString();

        while (contentText.endsWith("\n")) {
            contentText = contentText.substring(0, contentText.length() - 2);
        }


        avatarImageView.setImageDrawable(feedPost.getLogoDrawable());

        try {
            dateTextView.setVisibility(View.VISIBLE);
            dateTextView.setText(DateHelper.getStringDateWithTime(new Date(Long.parseLong(feedPost.getTimeStamp()) * 1000)));
        } catch (Exception e) {
            dateTextView.setVisibility(View.INVISIBLE);
        }
        authorTextView.setText(content.getAuthorName());
        contentTextView.setText(contentText);
        commentsTextView.setText(String.valueOf(content.getCommentsCount()));

        return view;
    }

    private boolean setAllReactionsDialog(ViewGroup view, FeedPostContent content, String eventKey, Drawable[] drawables) {
        RecyclerView reactionsRecyclerView = (RecyclerView) LayoutInflater.from(getContext())
                .inflate(R.layout.reactions_popup, view, false);
        PopupWindow popupWindow = new PopupWindow(reactionsRecyclerView,
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT);
        popupWindow.setBackgroundDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.solid_rounded));
        popupWindow.setOutsideTouchable(true);
        AllReactionsAdapter adapter = new AllReactionsAdapter(drawables);
        reactionsRecyclerView.setAdapter(adapter);
        reactionsRecyclerView.setLayoutManager(
                new LinearLayoutManager(
                        getContext(), LinearLayoutManager.HORIZONTAL,
                        false
                )
        );
        adapter.setOnItemClickListener(new LayoutHelper.OnItemClickListener() {
            @Override
            public void onItemClick(ViewGroup parent, View view, int position) {
                NetworkHelper.startAsyncTaskCatchingApiErrors(getContext(), () -> {
                    content.setLikes(diaryAPI.setLike_v6(
                            eventKey,
                            Reactions.getEmotion(position).getName()
                    ));
                    AsyncUtil.executeInMain(() -> {
                        notifyDataSetChanged();
                        popupWindow.dismiss();
                    });
                });
            }
        });
        popupWindow.setElevation(10);
        popupWindow.showAsDropDown(view);
        return true;
    }

    private void removeReaction(FeedPostContent content, String eventKey) {
        NetworkHelper.startAsyncTaskCatchingApiErrors(getContext(), () -> {
            content.setLikes(diaryAPI.removeLike_v6(eventKey));
            AsyncUtil.executeInMain(this::notifyDataSetChanged);
        });
    }
}

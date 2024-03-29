package ru.vladik.dnevnik.DiaryAPI;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.DiaryContext;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.FeedElement;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.FeedPost;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.ImportantRecent;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.webApi.FeedPostList;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.webApi.Likes;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.FormatedDate;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.FullMark;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.Lesson;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.LessonWithMarks;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.LessonWithoutWorks;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.Mark;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.Person;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.Schedule;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.Subject;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.TimeTable;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.v2.Work;
import ru.vladik.dnevnik.DiaryAPI.DataClasses.*;
import ru.vladik.dnevnik.DiaryAPI.Util.HttpUtil;
import ru.vladik.dnevnik.DiaryAPI.exceptions.JsonNotValidException;

public class DiaryAPI extends DiaryBase {

    private final Gson gson;

    public DiaryAPI(String login, String password) throws DiaryLoginException {
        super(login, password);
        gson = new Gson();
    }

    public DiaryContext getContext() {
        String contextJSONString = getWithAPIv2("users/me/context", null, true);
        Type contextTypeToken = new TypeToken<DiaryContext>() {
        }.getType();
        DiaryContext context = gson.fromJson(contextJSONString, contextTypeToken);
        context.setGroupIds(getEduGroups());
        return context;
    }

    public Schedule getScheduleWithOffset(Long groupId, Long personId, Long schoolId,
                                          int offsetStart) {
        List<LessonWithMarks> lessonWithMarksList = getWeekLessonsWithMarksWithOffset(groupId,
                personId, schoolId, offsetStart, offsetStart);
        Schedule schedule = Schedule.getScheduleFromLessonsWithMarks(lessonWithMarksList);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault());
        Date date = FormatedDate.startOfWeek(offsetStart).getDate();
        int dayOfWeek = 0;
        TimeUnit day = TimeUnit.DAYS;
        for (List<LessonWithMarks> list : schedule) {
            if (list.size() == 0) {
                Lesson lesson = new Lesson();
                lesson.getSubject().setName("Нет уроков");
                Date dateToSet = new Date(date.getTime() + day.toMillis(dayOfWeek));
                lesson.setDate(simpleDateFormat.format(dateToSet));
                list.add(new LessonWithMarks(lesson, new ArrayList<>()));
            }
            dayOfWeek++;
        }
        return schedule;
    }

    public List<FullMark> getRecentPersonMarks(Long personId, Long groupId) {
        Type markListTypeToken = new TypeToken<List<Mark>>() {
        }.getType();
        Type lessonListTypeToken = new TypeToken<List<LessonWithoutWorks>>() {
        }.getType();
        Type workListTypeToken = new TypeToken<List<Work>>() {
        }.getType();
        Type subjectListTypeToken = new TypeToken<List<Subject>>() {
        }.getType();
        List<FullMark> fullMarkList = new ArrayList<>();
        try {
            String method = "persons/" + personId + "/group/" + groupId + "/recentmarks";
            String jsonParentString = getWithAPIv2(method, null, true);
            JSONObject jsonParent = new JSONObject(jsonParentString);
            JSONArray marksJSON = jsonParent.getJSONArray("marks");
            JSONArray lessonsJSON = jsonParent.getJSONArray("lessons");
            JSONArray worksJson = jsonParent.getJSONArray("works");
            JSONArray subjectsJson = jsonParent.getJSONArray("subjects");
            List<Mark> markList = gson.fromJson(String.valueOf(marksJSON), markListTypeToken);
            List<LessonWithoutWorks> lessonList = gson.fromJson(String.valueOf(lessonsJSON),
                    lessonListTypeToken);
            List<Work> workList = gson.fromJson(String.valueOf(worksJson), workListTypeToken);
            List<Subject> subjectList = gson.fromJson(String.valueOf(subjectsJson),
                    subjectListTypeToken);
            for (Mark mark : markList) {
                if (mark.getLesson() != null) {
                    for (LessonWithoutWorks lessonWithoutWorks : lessonList) {
                        if (mark.getLesson().longValue() == lessonWithoutWorks.getId().longValue()) {
                            Lesson lesson = new Lesson(lessonWithoutWorks);
                            for (Work work : workList) {
                                if (lessonWithoutWorks.getWorks().contains(work.getId())) {
                                    lesson.getWorks().add(work);
                                }
                            }
                            for (Subject subject : subjectList) {
                                if (lesson.getSubjectId().longValue() == subject.getId().longValue()) {
                                    lesson.setSubject(subject);
                                }
                            }
                            fullMarkList.add(new FullMark(mark, lesson, lesson.getSubject()));
                            break;
                        }
                    }
                } else {
                    Subject subject = null;
                    for (Work work : workList) {
                        if (mark.getWork().longValue() == work.getId()) {
                            for (Subject subject1 : subjectList) {
                                if (subject1.getId().longValue() == work.getSubjectId().longValue()) {
                                    subject = subject1;
                                }
                            }
                        }
                    }
                    fullMarkList.add(new FullMark(mark, new Lesson(), subject));
                }
            }
            return fullMarkList;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public List<Lesson> getGroupLessonsInfo(Long groupId, FormatedDate dateStart,
                                            FormatedDate dateEnd) {
        Type lessonListTypeToken = new TypeToken<List<Lesson>>() {
        }.getType();
        String method = "edu-groups/" + groupId + "/lessons/" + dateStart.toString() + "/" +
                dateEnd.toString();
        String jsonParentString = getWithAPIv2(method, null, true);
        return gson.fromJson(jsonParentString, lessonListTypeToken);
    }

    public List<LessonWithMarks> getWeekLessonsWithMarksWithOffset(Long groupId, Long personId,
                                                                   Long schoolId, int offsetStart,
                                                                   int offsetEnd) {
        List<Lesson> lessonList = getWeekGroupLessonsInfoWithOffset(groupId, offsetStart, offsetEnd);
        List<Mark> markList = getPersonMarksWithOffset(personId, schoolId, offsetStart, offsetEnd);
        List<LessonWithMarks> lessonWithMarksList = new ArrayList<>();
        for (Lesson lesson : lessonList) {
            LessonWithMarks lessonWithMarks = new LessonWithMarks(lesson, new ArrayList<>());
            for (Mark mark : markList) {
                if (lesson.getId().longValue() == mark.getLesson().longValue()) {
                    lessonWithMarks.getMarks().add(mark);
                }
            }
            lessonWithMarksList.add(lessonWithMarks);
        }
        return lessonWithMarksList;
    }

    public List<LessonWithMarks> getWeekLessonsWithMarks(Long groupId, Long personId, Long schoolId) {
        return getWeekLessonsWithMarksWithOffset(groupId, personId, schoolId, 0, 0);
    }

    public List<Mark> getPersonMarksWithOffset(Long personId, Long schoolId, int offsetStart, int offsetEnd) {
        Type lessonMarkTypeToken = new TypeToken<List<Mark>>() {
        }.getType();
        FormatedDate dateStart = FormatedDate.startOfWeek(offsetStart);
        FormatedDate dateEnd = FormatedDate.endOfWeek(offsetEnd);
        String method = "persons/" + personId + "/schools/" + schoolId + "/marks/" + dateStart + "/" +
                dateEnd;
        String jsonParentString = getWithAPIv2(method, null, true);
        return gson.fromJson(jsonParentString, lessonMarkTypeToken);
    }

    public List<Lesson> getWeekGroupLessonsInfo(Long groupId) {
        return getWeekGroupLessonsInfoWithOffset(groupId, 0, 0);
    }

    public List<Lesson> getWeekGroupLessonsInfoWithOffset(Long groupId, int offsetStart, int offsetEnd) {
        FormatedDate dateStart = FormatedDate.startOfWeek(offsetStart);
        FormatedDate dateEnd = FormatedDate.endOfWeek(offsetEnd);
        Type lessonListTypeToken = new TypeToken<List<Lesson>>() {
        }.getType();
        String method = "edu-groups/" + groupId + "/lessons/" + dateStart.toString() + "/" + dateEnd.toString();
        String jsonParentString = getWithAPIv2(method, null, true);
        return gson.fromJson(jsonParentString, lessonListTypeToken);
    }

    public TimeTable getGroupTimeTable(Long groupId) {
        Type timeTableTypeToken = new TypeToken<TimeTable>() {
        }.getType();
        String method = "edu-groups/" + groupId + "/timetables";
        String timeTableString = getWithAPIv2(method, null, true);
        return gson.fromJson(timeTableString, timeTableTypeToken);
    }

    public List<Long> getEduGroups() {
        Type longListToken = new TypeToken<List<Long>>() {
        }.getType();
        String method = "users/me/edu-groups";
        String groupsString = getWithAPIv2(method, null, true);
        return gson.fromJson(groupsString, longListToken);
    }

    public Lesson getLesson(Long lessonId) {
        try {
            Type lessonTypeToken = new TypeToken<Lesson>() {
            }.getType();
            String method = "lessons/" + lessonId;
            String lessonJsonString = getWithAPIv2(method, null, true);
            return gson.fromJson(lessonJsonString, lessonTypeToken);
        } catch (Exception e) {
            return new Lesson();
        }
    }

    public FeedPostList getEduGroupPosts(Long schoolId, Long groupId, @Nullable String date, @Nullable Integer count) {
        Type feedPostListToken = new TypeToken<FeedPostList>() {
        }.getType();
        String method = "posts/topic/school_" + schoolId + "_group_" + groupId;
        String postsString = getWithAPInoV(method, new HttpUtil.MapWith_p<String, String>()
                        .p("date", String.valueOf(date))
                        .p("take", String.valueOf(count)),
                true);
        return gson.fromJson(postsString, feedPostListToken);
    }

    public List<Person> getGroupPersons(Long groupId) {
        Type personListType = new TypeToken<List<Person>>() {
        }.getType();
        String personListString = getWithAPIv2("persons",
                new HttpUtil.MapWith_p<String, String>().p("eduGroup", groupId.toString()), true);
        return gson.fromJson(personListString, personListType);
    }

    public Mark getMarkById(Long id) {
        Type markType = new TypeToken<Mark>() {}.getType();
            String method = "marks/" + id;
        String markString = getWithAPIv2(method, null, true);
        return gson.fromJson(markString, markType);
    }

    public List<Mark> getMarksByWork(Long workId) {
        Type markListType = new TypeToken<List<Mark>>() {
        }.getType();
        String method = "works/" + workId + "/marks";
        String markListString = getWithAPIv2(method, null, true);
        return gson.fromJson(markListString, markListType);
    }

    public Likes setReactionOnPost(String eventKey, String reaction) {
        Type likesToken = new TypeToken<Likes>() {
        }.getType();
        String method = "posts/likes/" + eventKey + "/" + reaction;
        String likesString = postAPInoV(method, null, null, true);
        return gson.fromJson(likesString, likesToken);
    }

    public List<Subject> getGroupSubjects(Long groupId) {
        Type subjectListTypeToken = new TypeToken<List<Subject>>() {
        }.getType();
        String method = "edu-groups/" + groupId + "/subjects";
        String subjectsString = getWithAPIv2(method, null, true);
        return gson.fromJson(subjectsString, subjectListTypeToken);
    }

    public Work getWorkById(Long workId) {
        Type workType = new TypeToken<Work>() {
        }.getType();
        String method = "works/" + workId;
        String markListString = getWithAPIv2(method, null, true);
        return gson.fromJson(markListString, workType);
    }

    public ImportantRecent getRecentNews(Long personId, Long groupId) {
        Type newsType = new TypeToken<ImportantRecent>() {
        }.getType();
        Type newsPostType = new TypeToken<FeedPost>() {
        }.getType();
        String method = "persons/" + personId + "/groups/" + groupId + "/important";
        String newsString = getWithAPIv6Mobile(method, null, true);
        ImportantRecent news = gson.fromJson(newsString, newsType);
        try {
            news.setFeed(new ArrayList<>());
            JSONArray feed = new JSONObject(newsString).getJSONArray("feed");
            for (int i = 0; i < feed.length(); i++) {
                JSONObject o = feed.getJSONObject(i);
                if (o.get("type").equals(FeedElement.POST)) {
                    news.getFeed().add(gson.fromJson(o.toString(), newsPostType));
                }
            }
        } catch (JSONException e) {
            throw new JsonNotValidException();
        }
        return news;
    }

    public ImportantRecent getRecentNewsWithSubjectNames(Long personId, Long groupId) {
        ImportantRecent news = getRecentNews(personId, groupId);
        List<Subject> subjects = getGroupSubjects(groupId);
        for (ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.Mark mark : news.getRecentMarks()) {
            for (Subject subject : subjects) {
                if (mark.getSubject().getId().equals(subject.getId())) {
                    mark.getSubject().setName(subject.getName());
                    break;
                }
            }
        }
        return news;
    }

    public ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.Likes setLike_v6(String eventKey, String reaction) {
        Type likesType = new TypeToken<ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.Likes>() {}.getType();
        String method = "likes/" + eventKey + "/set";
        String likesString = postWithAPIv6Mobile(method, null,
                new HttpUtil.MapWith_p<String, String>().p("userVote", reaction), true);
        return gson.fromJson(likesString, likesType);
    }

    public ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.Likes removeLike_v6(String eventKey) {
        Type likesType = new TypeToken<ru.vladik.dnevnik.DiaryAPI.DataClasses.v6.Likes>() {}.getType();
        String method = "likes/" + eventKey + "/remove";
        String likesString = postWithAPIv6Mobile(method, null, null, true);
        return gson.fromJson(likesString, likesType);
    }
}

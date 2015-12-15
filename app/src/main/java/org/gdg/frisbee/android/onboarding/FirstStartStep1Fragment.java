/*
 * Copyright 2013-2015 The GDG Frisbee Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gdg.frisbee.android.onboarding;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.util.Collections;
import java.util.List;

import org.gdg.frisbee.android.Const;
import org.gdg.frisbee.android.R;
import org.gdg.frisbee.android.api.model.Chapter;
import org.gdg.frisbee.android.api.model.Directory;
import org.gdg.frisbee.android.app.App;
import org.gdg.frisbee.android.cache.ModelCache;
import org.gdg.frisbee.android.chapter.ChapterAdapter;
import org.gdg.frisbee.android.chapter.ChapterComparator;
import org.gdg.frisbee.android.common.BaseFragment;
import org.gdg.frisbee.android.utils.PrefUtils;
import org.gdg.frisbee.android.view.AutoCompleteSpinnerView;
import org.gdg.frisbee.android.view.ColoredSnackBar;
import org.joda.time.DateTime;

import butterknife.Bind;
import butterknife.ButterKnife;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import timber.log.Timber;

public class FirstStartStep1Fragment extends BaseFragment {

    private static final String ARG_SELECTED_CHAPTER = "selected_chapter";
    @Bind(R.id.chapter_spinner)
    AutoCompleteSpinnerView mChapterSpinner;
    @Bind(R.id.confirm)
    Button mConfirm;
    @Bind(R.id.viewSwitcher)
    ViewSwitcher mLoadSwitcher;
    private ChapterAdapter mSpinnerAdapter;
    private Chapter mSelectedChapter;
    private ChapterComparator mLocationComparator;

    private final TextWatcher disableConfirmAfterTextChanged = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            mConfirm.setEnabled(false);
        }
    };

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mSpinnerAdapter != null && mSpinnerAdapter.getCount() > 0) {
            outState.putParcelable(ARG_SELECTED_CHAPTER, mSpinnerAdapter.getItem(mChapterSpinner.getListSelection()));
        }

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);

        mLocationComparator = new ChapterComparator(PrefUtils.getHomeChapterId(getActivity()));

        mSpinnerAdapter = new ChapterAdapter(getActivity(), R.layout.spinner_item_welcome);
        mSpinnerAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);

        if (savedInstanceState != null) {
            mSelectedChapter = savedInstanceState.getParcelable(ARG_SELECTED_CHAPTER);
        }

        App.getInstance().getModelCache().getAsync(
                Const.CACHE_KEY_CHAPTER_LIST_HUB, new ModelCache.CacheListener() {
                    @Override
                    public void onGet(Object item) {
                        Directory directory = (Directory) item;
                        addChapters(directory.getGroups());
                        mLoadSwitcher.setDisplayedChild(1);
                    }

                    @Override
                    public void onNotFound(String key) {
                        fetchChapters();
                    }
                }
        );

        mChapterSpinner.setThreshold(1);

        Filter.FilterListener enableConfirmOnUniqueFilterResult = new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                mConfirm.setEnabled(count == 1);
                if (count == 1) {
                    mSelectedChapter = mSpinnerAdapter.getItem(0);
                }
            }
        };
        AdapterView.OnItemClickListener enableConfirmOnChapterClick = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mSelectedChapter = mSpinnerAdapter.getItem(position);
                mConfirm.setEnabled(true);
            }
        };

        mChapterSpinner.setFilterCompletionListener(enableConfirmOnUniqueFilterResult);
        mChapterSpinner.setOnItemClickListener(enableConfirmOnChapterClick);
        mChapterSpinner.addTextChangedListener(disableConfirmAfterTextChanged);

        mChapterSpinner.setOnTouchListener(new ChapterSpinnerTouchListener());

        mConfirm.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (getActivity() instanceof Step1Listener) {
                            ((Step1Listener) getActivity()).onConfirmedChapter(mSelectedChapter);
                        }
                    }
                }
        );
    }

    private void fetchChapters() {

        App.getInstance().getGdgXHub().getDirectory(
                new Callback<Directory>() {

                    @Override
                    public void success(final Directory directory, Response response) {

                        addChapters(directory.getGroups());
                        mLoadSwitcher.setDisplayedChild(1);
                        App.getInstance().getModelCache().putAsync(
                                Const.CACHE_KEY_CHAPTER_LIST_HUB,
                                directory,
                                DateTime.now().plusDays(4),
                                null
                        );
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        try {
                            Snackbar snackbar = Snackbar.make(
                                    getView(), R.string.fetch_chapters_failed,
                                    Snackbar.LENGTH_INDEFINITE
                            );
                            snackbar.setAction(
                                    "Retry", new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            fetchChapters();
                                        }
                                    }
                            );
                            ColoredSnackBar.alert(snackbar).show();
                        } catch (IllegalStateException exception) {
                            Toast.makeText(getActivity(), R.string.fetch_chapters_failed, Toast.LENGTH_SHORT).show();
                        }
                        Timber.e(error, "Could'nt fetch chapter list");
                    }
                }
        );
    }

    private void addChapters(List<Chapter> chapterList) {
        Collections.sort(chapterList, mLocationComparator);
        mSpinnerAdapter.clear();
        mSpinnerAdapter.addAll(chapterList);

        mChapterSpinner.setAdapter(mSpinnerAdapter);

        if (mSelectedChapter != null) {
            int pos = mSpinnerAdapter.getPosition(mSelectedChapter);
            mChapterSpinner.setSelection(pos);
        } else {
            if (chapterList.size() > 0) {
                mSelectedChapter = chapterList.get(0);
            }
        }
        if (mSelectedChapter != null) {
            mChapterSpinner.setText(mSelectedChapter.toString());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_welcome_step1, container, false);
        ButterKnife.bind(this, v);
        return v;
    }

    private void showAllChaptersByLocation() {
        mChapterSpinner.setText("");
        new AlertDialog.Builder(getContext())
                .setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                mSelectedChapter = mSpinnerAdapter.getItem(position);
                                mChapterSpinner.setText(mSelectedChapter.toString());
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {

                            }
                        }
                )
                .setAdapter(
                        mSpinnerAdapter, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int position) {
                                mSelectedChapter = mSpinnerAdapter.getItem(position);
                                mChapterSpinner.setText(mSelectedChapter.toString(), false);
                                mConfirm.setEnabled(true);
                            }
                        }
                ).show();
    }

    public interface Step1Listener {
        void onConfirmedChapter(Chapter chapter);
    }

    private class ChapterSpinnerTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            final int drawableRight = 2;

            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (mChapterSpinner.getRight()
                        - mChapterSpinner.getCompoundDrawables()[drawableRight].getBounds().width())) {
                    showAllChaptersByLocation();
                    return true;
                }
            }
            return false;
        }
    }
}

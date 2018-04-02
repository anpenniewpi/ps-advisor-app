package org.fundacionparaguaya.advisorapp.ui.survey.priorities;

import android.app.Activity;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.AppCompatImageView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import org.fundacionparaguaya.advisorapp.AdvisorApplication;
import org.fundacionparaguaya.advisorapp.R;
import org.fundacionparaguaya.advisorapp.data.model.*;
import org.fundacionparaguaya.advisorapp.injection.InjectionViewModelFactory;
import org.fundacionparaguaya.advisorapp.ui.common.widget.NumberStepperView;
import org.fundacionparaguaya.advisorapp.util.IndicatorUtilities;
import org.fundacionparaguaya.advisorapp.util.KeyboardUtils;

import javax.inject.Inject;
import java.util.Date;

/**
 * Pop up window that allows the user to input some details about the priority..
 *
 * Three questions
 *
 * 1. Why they don't have the priority
 * 2. What will they do to get the priority
 * 3. When they will get the priority
 *
 * Can be used both to edit an existing priority and create a new one.
 */
public class EditPriorityActivity extends FragmentActivity implements View.OnClickListener, TextWatcher {

    private static String INDICATOR_LEVEL_ARG = "INDICATOR_LEVEL_KEY";
    private static String INDICATOR_INDEX_ARG = "INDICATOR_INDEX_ARG";
    private static String SURVEY_ID_ARG = "SURVEY_ID_ARG";
    private static String RESPONSE_REASON_ARG = "RESPONSE_WHY_KEY";
    private static String RESPONSE_ACTION_ARG = "RESPONSE_ACTION_KEY";
    private static String RESPONSE_DATE_ARG = "RESPONSE_DATE_KEY";

    private static int MAX_MONTHS = 48;
    private static int MIN_MONTHS = 1;

    private TextView mTvProgress;
    private AppCompatImageView mIndicatorColor;
    private TextView mIndicatorTitle;

    private ScrollView mScrollView;

    private Button mBtnSubmit;
    private ImageButton mBtnExit;

    private TextView mTvWhy;
    private EditText mEtWhy;

    private TextView mTvHow;
    private EditText mEtStrategy;

    private TextView mTvWhen;
    private NumberStepperView mMonthsStepper;

    @Inject
    protected InjectionViewModelFactory mViewModelFactory;

    private EditPriorityViewModel mViewModel;

    protected void onResume()
    {
        super.onResume();
        overridePendingTransition(R.anim.slide_up, R.anim.stay);
    }

    protected void onPause()
    {
        overridePendingTransition(R.anim.stay, R.anim.slide_down);
        super.onPause();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //inject the view model factory
        ((AdvisorApplication)getApplication())
                .getApplicationComponent()
                .inject(this);

        //inject dependencies for view model
        mViewModel = ViewModelProviders
                .of(this, mViewModelFactory)
                .get(EditPriorityViewModel.class);

        setContentView(R.layout.activity_editpriority);

        mTvProgress = findViewById(R.id.tv_editpriority_progress);

        mIndicatorColor = findViewById(R.id.iv_prioritypopup_color);
        mIndicatorTitle = findViewById(R.id.tv_prioritypopup_title);

        mEtWhy = findViewById(R.id.et_prioritypopup_why);
        mEtStrategy = findViewById(R.id.et_prioritypopup_strategy);

        mTvHow = findViewById(R.id.tv_editpriority_how);
        mTvWhen = findViewById(R.id.tv_editpriority_when);
        mTvWhy = findViewById(R.id.tv_editpriority_why);

        mBtnSubmit = findViewById(R.id.btn_prioritypopup_submit);
        mBtnExit = findViewById(R.id.btn_prioritypopup_close);

        mMonthsStepper = findViewById(R.id.numberstepper_editpriority);

        mMonthsStepper.setMinValue(MIN_MONTHS);
        mMonthsStepper.setMaxValue(MAX_MONTHS);

        mScrollView = findViewById(R.id.scroll_view);

        //region Load Arguments
        int surveyId = getIntent().getIntExtra(SURVEY_ID_ARG, -1);
        int questionIndex = getIntent().getIntExtra(INDICATOR_INDEX_ARG, -1);

        mViewModel.setIndicator(surveyId, questionIndex);

        IndicatorOption.Level level = IndicatorOption.Level.valueOf(getIntent().getStringExtra(INDICATOR_LEVEL_ARG));
        IndicatorUtilities.setColorFromLevel(level, mIndicatorColor);

        if(level == IndicatorOption.Level.Green)
        {
            displayAsAchievement();
        }

        if(savedInstanceState == null) //if we are loading for the first time, init view model from arguments
        {
           viewModelInit();
        }

        mEtWhy.setText(mViewModel.getReason());
        mEtStrategy.setText(mViewModel.getAction());
        mMonthsStepper.setCurrentValue(mViewModel.getMonthsUntilCompletion());

        addListeners();
    }

    private void displayAsAchievement()
    {
        mMonthsStepper.setVisibility(View.INVISIBLE);
        mTvWhen.setVisibility(View.INVISIBLE);
        mViewModel.setWhenSeen();

        mTvWhy.setText(R.string.prioritypopup_achievementwhat);
        mTvHow.setText(R.string.prioritypopup_achievementhow);
    }

    private void viewModelInit()
    {
        String reason = getIntent().getStringExtra(RESPONSE_REASON_ARG);
        mViewModel.setReason(reason);

        String action = getIntent().getStringExtra(RESPONSE_ACTION_ARG);
        mViewModel.setAction(action);

        Date date = (Date)getIntent().getSerializableExtra(RESPONSE_DATE_ARG);
        mViewModel.setCompletionDate(date);
    }

    public void addListeners()
    {
        mMonthsStepper.getValue().observe(this, mViewModel::setNumMonths);
        mBtnExit.setOnClickListener(this);
        mBtnSubmit.setOnClickListener(this);
        mEtWhy.addTextChangedListener(this);
        mEtStrategy.addTextChangedListener(this);

        mScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            Rect scrollBounds = new Rect();
            mScrollView.getDrawingRect(scrollBounds);

            float top = mMonthsStepper.getY();
            float bottom = top + mMonthsStepper.getHeight();

            if (mMonthsStepper.isShown() && scrollBounds.top < top && scrollBounds.bottom > bottom) {
                mViewModel.setWhenSeen();
            }
        });

        mViewModel.NumberOfQuestionsUnanswered().observe(this, numUnanswered->
        {
            if(mViewModel.areRequirementsMet())
            {
                mBtnSubmit.setVisibility(View.VISIBLE);
                mTvProgress.setVisibility(View.GONE);
            }
            else
            {
                mBtnSubmit.setVisibility(View.GONE);
                mTvProgress.setVisibility(View.VISIBLE);

                String progressText = String.format(getString(R.string.survey_questionsremaining), numUnanswered);
                mTvProgress.setText(progressText);
            }
        });

        mViewModel.getIndicator().observe(this, indicator->
        {
            if(indicator!=null) {
                mIndicatorTitle.setText(indicator.getIndicator().getTitle());
            }
        });
    }

    @Override
    public void onClick(View view) {
        if(this.getCurrentFocus()!=null) {
            KeyboardUtils.hideKeyboard(this.getCurrentFocus(),this);
        }

        if(view.equals(mBtnExit))
        {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
        else if(view.equals(mBtnSubmit) && mViewModel.areRequirementsMet())
        {
            //region Build Result

            //getIntent so that initial arguments are included with result
            Intent result = getIntent();
            result.putExtra(RESPONSE_ACTION_ARG, mViewModel.getAction());
            result.putExtra(RESPONSE_REASON_ARG, mViewModel.getReason());
            result.putExtra(RESPONSE_DATE_ARG, mViewModel.getCompletionDate());
            //endRegion

            setResult(Activity.RESULT_OK, result);
            this.finish();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        if(s == mEtWhy.getEditableText())
        {
            mViewModel.setReason(s.toString());
        }
        else if(s == mEtStrategy.getEditableText())
        {
            mViewModel.setAction(s.toString());
        }
    }


    public static Intent build(Context c, Survey s, IndicatorOption response) {
        return build(c, s, response,  null);
    }

    public static Intent build(Context c, Survey s, IndicatorOption response, LifeMapPriority priority) {

        IndicatorQuestion indicatorQuestion = null;

        for(IndicatorQuestion q: s.getIndicatorQuestions())
        {
            if(q.getIndicator().equals(response.getIndicator()))
            {
                indicatorQuestion = q;
            }
        }

        if(indicatorQuestion == null)
        {
            new Exception("IndicatorQuestion with indicator specified not found in survey...").printStackTrace();
        }

        return build(c, s, indicatorQuestion, response.getLevel(), priority);
    }

    public static Intent build(Context c, Survey s, IndicatorQuestion indicator, IndicatorOption.Level response,
                        LifeMapPriority priority) {

        Intent intent = new Intent(c, EditPriorityActivity.class);

        intent.putExtra(INDICATOR_LEVEL_ARG, response.name());
        intent.putExtra(INDICATOR_INDEX_ARG, s.getIndicatorQuestions().indexOf(indicator));
        intent.putExtra(SURVEY_ID_ARG, s.getId());

        if(priority != null)
        {
            intent.putExtra(RESPONSE_ACTION_ARG, priority.getAction());
            intent.putExtra(RESPONSE_REASON_ARG, priority.getReason());
            intent.putExtra(RESPONSE_DATE_ARG, priority.getEstimatedDate());
        }

        return intent;
    }

    public static Indicator getIndicatorFromResult(Intent result, Survey s)
    {
        int index = result.getIntExtra(INDICATOR_INDEX_ARG, -1);
        if(index == -1) new Exception("Result from EditPriorityActivity is malformed. No question index found.").printStackTrace();
        return s.getIndicatorQuestions().get(index).getIndicator();
    }

    public static LifeMapPriority processResult(Intent result, Survey s)
    {
        return LifeMapPriority.builder()
                .indicator(getIndicatorFromResult(result, s))
                .reason(result.getStringExtra(RESPONSE_REASON_ARG))
                .action(result.getStringExtra(RESPONSE_ACTION_ARG))
                .estimatedDate((Date)result.getSerializableExtra(RESPONSE_DATE_ARG))
                .isAchievement(result.getStringExtra(INDICATOR_LEVEL_ARG).equals("Green"))
                        //TODO: match to field determining whether this is an achievement
                .build();
    }
    //endregion
}

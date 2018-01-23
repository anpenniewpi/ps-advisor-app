package org.fundacionparaguaya.advisorapp.viewmodels;

import android.arch.lifecycle.*;
import android.support.annotation.Nullable;
import org.fundacionparaguaya.advisorapp.models.*;
import org.fundacionparaguaya.advisorapp.repositories.FamilyRepository;
import org.fundacionparaguaya.advisorapp.repositories.SurveyRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Survey view model that is shared across all of the fragments/activity related to the view model
 */

public class SharedSurveyViewModel extends ViewModel
{
    public enum SurveyState {NONE, INTRO, BACKGROUND_QUESTIONS, INDICATORS, REVIEW}

    static String NO_SNAPSHOT_EXCEPTION_MESSAGE = "Method call requires an existing snapshot, but no snapshot has been created. (Call" +
            "makeSnapshot before this function";

    SurveyRepository mSurveyRepository;
    FamilyRepository mFamilyRepository;

    MutableLiveData<SurveyProgress> mProgress = new MutableLiveData<SurveyProgress>();
    private MutableLiveData<SurveyState> mSurveyState;

    List<IndicatorQuestion> mSkippedIndicators;

    MutableLiveData<Snapshot> mSnapshot;

    LiveData<Family> mFamily;

    Survey mSurvey;

    private int mSurveyId;
    private int mFamilyId;

    public SharedSurveyViewModel(SurveyRepository surveyRepository, FamilyRepository familyRepository) {
        super();

        mSurveyRepository = surveyRepository;
        mFamilyRepository = familyRepository;

        mProgress.setValue(new SurveyProgress());
        mSurveyState = new MutableLiveData<>();

        mSurveyState.setValue(SurveyState.NONE);
        mSnapshot = new MutableLiveData<Snapshot>();

        mSkippedIndicators = new ArrayList<>();
    }

    public LiveData<Family> getCurrentFamily()
    {
        return mFamily;
    }

    /**
     * Sets the family that is taking the survey
     *
     * @param familyId Id of the family taking the survey
     */
    public void setFamily(int familyId)
    {
        mFamilyId = familyId;
        mFamily = mFamilyRepository.getFamily(familyId);
    }
    /**
     * Makes a new snapshot based on the family set and the survey provided.
     *
     * Assumes that family live data object .getValue is not null
     *
     * We should wait for this before proceeding from the start screen to the next screen
     *
     */
    public void makeSnapshot(Survey survey)
    {
        mSurvey = survey;

        mSnapshot.setValue(new Snapshot(mFamily.getValue(), mSurvey));
    }

    public LiveData<Snapshot> getSnapshot()
    {
        return mSnapshot;
    }

    public Survey getSurveyInProgress()
    {
        return mSurvey;
    }

    /**
     * Returns the surveys available to take.
     */
    public LiveData<List<Survey>> getSurveys()
    {
        return mSurveyRepository.getSurveys();
    }

    public MutableLiveData<SurveyState> getSurveyState()
    {
        if(mSurveyState==null)
        {
            mSurveyState = new MutableLiveData<SurveyState>();
        }

        return mSurveyState;
    }

    public MutableLiveData<SurveyProgress> getProgress()
    {
        return mProgress;
    }

    public void addSkippedIndicator(IndicatorQuestion question) {
        mSkippedIndicators.add(question);
    }

    public List<IndicatorQuestion> getSkippedIndicators()
    {
        return mSkippedIndicators;
    }

    public @Nullable IndicatorOption getResponseForIndicator(IndicatorQuestion question)
    {
        if(hasExistingSnapshot()) {
            return mSnapshot.getValue().getIndicatorResponses().get(question);
        }

        return null;
    }

    public void addIndicatorResponse(IndicatorQuestion indicator, IndicatorOption response)
    {
        if(hasExistingSnapshot())
        {
            mSnapshot.getValue().response(indicator, response);
        }
    }

    public void addBackgroundResponse(SurveyQuestion question, String response)
    {
        if(hasExistingSnapshot())
        {
            if(question instanceof PersonalQuestion)
            {
                mSnapshot.getValue().response((PersonalQuestion) question, response);
            }
            else if(question instanceof EconomicQuestion)
            {
                mSnapshot.getValue().response((EconomicQuestion) question, response);
            }
        }
    }

    private boolean hasExistingSnapshot()
    {
        if(mSnapshot == null)
        {
            throw new IllegalStateException(NO_SNAPSHOT_EXCEPTION_MESSAGE);
        }

        else return true;
    }


    public static class SurveyProgress
    {
        String mProgressDescription;
        int mPercentageComplete;

        void setDescription(String description)
        {
            mProgressDescription = description;
        }

        void setPercentageComplete(int percentage)
        {
            mPercentageComplete = percentage;
        }

        String getDescription()
        {
            return mProgressDescription;
        }

        int getPercentageComplete()
        {
            return mPercentageComplete;
        }
    }
}

package com.refactoring.noteorganizerproject.notes.notes_list_fragment.presenter;

import android.text.TextWatcher;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.refactoring.noteorganizerproject.entity.data_base.dao.NoteDAO;
import com.refactoring.noteorganizerproject.entity.data_base.impl.NoteDaoImpl;
import com.refactoring.noteorganizerproject.entity.data_base.interract.INoteDao;
import com.refactoring.noteorganizerproject.entity.shared_prefs.SharedPreferencesManager;
import com.refactoring.noteorganizerproject.notes.model.Note;
import com.refactoring.noteorganizerproject.notes.notes_list_fragment.view.NotesFragment;
import com.refactoring.noteorganizerproject.notes.notes_list_fragment.view.recycler.IViewHolder;
import com.refactoring.noteorganizerproject.utils.SortListComparator;

import java.util.Comparator;

/**
 * Base Notes Presenter
 *
 * Seems it has to implement BasePresenter interface and it should be given to Adapter class
 * to interact with data.
 *
 * Generates sample data.
 */

public class NotesPresenter implements INotesPresenter, INotesSortingPresenter {
    private String CLASS_TAG = "RecyclerViewPresenter";
    private final int NEW_NOTE = -1;
    private final int NO_MESSAGE = 0;

    private final INoteDao noteDao = new NoteDaoImpl();

    private NotesFragment fragmentView;
    private SharedPreferencesManager appSettings;
    private NotesListenersProvider listenersProvider;

    enum State { MULTI_SELECTION, SINGLE_SELECTION }
    private State state = State.SINGLE_SELECTION;

    private Comparator<Note> comparator = SortListComparator.getDateComparator();

    public NotesPresenter(NotesFragment view) {
        this.fragmentView = view;
        appSettings = noteDao.getAppSettings();
        listenersProvider = new NotesListenersProvider(this);
    }

    public void getNotes() {
        String searchText = fragmentView.getSearchText();
        if (searchText.equals(""))
            noteDao.getFromDB(this);
        else
            searchNotes(searchText);
    }

    @Override
    public void notifyDatasetChanged(int messageId) {
        if (messageId == NO_MESSAGE)
            fragmentView.notifyDataChanged();
        else
            fragmentView.showToast(messageId);
    }

    @Override
    public void bindView(IViewHolder viewHolder) {
        int position = viewHolder.getPos();
        Note note = noteDao.getNoteByPosition(position);
        viewHolder.setNote(note);
    }

    @Override
    public int getItemCount() {
        return noteDao.size();
    }

    @Override
    public void clicked(int position) {
        if (state == State.SINGLE_SELECTION)
            viewNote(position);
        else
            selectNote(position);
    }
    @Override
    public void createNewNote() {
        disableSort();
        disableMultiSelection();
        appSettings.setClickedNoteId(NEW_NOTE);
        fragmentView.viewNote();
    }

    private void viewNote(int position) {
        if (position == NEW_NOTE)
            appSettings.setClickedNoteId(NEW_NOTE);
        else {
            Note note = noteDao.getNoteByPosition(position);
            appSettings.setClickedNoteId(note.id);
        }
        fragmentView.viewNote();
    }
    private void selectNote(int position) {
        Note note = noteDao.getNoteByPosition(position);
        noteDao.selectNote(note);
        fragmentView.setCardSelected(noteDao.wasSelected(note), position);
    }

    @Override
    public void longClicked(int position, View v) {
        if (state != State.MULTI_SELECTION) {
            enableMultiSelection();
            selectNote(position);
        }
    }

    @Override
    public void searchNotes(String s) {
        if (s.length() == 0)
            getNotes();
        else
            noteDao.search(this, s);
    }
    @Override
    public void clearSearch() {
        fragmentView.clearSearch();
    }

    @Override
    public void enableSort() {
        disableMultiSelection();
        fragmentView.setSortLayoutVisibility(true);
        fragmentView.setExtraOptionsLayoutVisibility(false);
        changeStateTo(State.SINGLE_SELECTION);
        updateByState();
    }

    @Override
    public void disableSort() {
        fragmentView.setSortLayoutVisibility(false);
        changeStateTo(State.SINGLE_SELECTION);
        updateByState();
    }

    @Override
    public void sortByTitle() {
        comparator = SortListComparator.getNameComparator();
        sort();
    }

    @Override
    public void sortByDate() {
        comparator = SortListComparator.getDateComparator();
        sort();
    }

    @Override
    public void sortByDefault() {
        comparator = SortListComparator.getNumberComparator();
        sort();
    }

    private void sort() {
        noteDao.sortNotes(this, comparator);
    }

    @Override
    public void enableMultiSelection() {
        disableSort();
        fragmentView.setExtraOptionsLayoutVisibility(true);
        changeStateTo(State.MULTI_SELECTION);
    }

    @Override
    public void disableMultiSelection() {
        fragmentView.setExtraOptionsLayoutVisibility(false);
        changeStateTo(State.SINGLE_SELECTION);
        fragmentView.setCardsToDefaultStyle();
        updateByState();
    }

    private void changeStateTo(State newState) {
        state = newState;
    }
    private void updateByState() {
        if (state == State.SINGLE_SELECTION) {
            noteDao.clearSelected();
        }
    }


    public void unsubscribe() {
        noteDao.unsubscribe();
    }

    @Override
    public void deleteNotes() {
        noteDao.deleteSelectedNotes(this);
        fragmentView.setCardsToDefaultStyle();
    }

    @Override
    public void migrateSelectedNotes() {
        noteDao.migrateSelected(this);
    }

    @Override
    public TextWatcher getTextChangeListener(View buttonClear) {
        return listenersProvider.getTextChangeListener(buttonClear);
    }

    @Override
    public ChipGroup.OnCheckedChangeListener getOnCheckedChangeListener() {
        return listenersProvider.getOnCheckedChangeListener();
    }

    @Override
    public RecyclerView.OnScrollListener getRecyclerScrollListener(FloatingActionButton fab){
        return listenersProvider.getRecyclerScrollListener(fab);
    }
}

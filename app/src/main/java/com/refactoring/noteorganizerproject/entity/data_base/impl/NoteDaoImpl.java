package com.refactoring.noteorganizerproject.entity.data_base.impl;

import com.refactoring.noteorganizerproject.BasePresenter;
import com.refactoring.noteorganizerproject.R;
import com.refactoring.noteorganizerproject.entity.AppConfig;
import com.refactoring.noteorganizerproject.entity.data_base.dao.NoteDAO;
import com.refactoring.noteorganizerproject.entity.data_base.interract.INoteDao;
import com.refactoring.noteorganizerproject.entity.shared_prefs.SharedPreferencesManager;
import com.refactoring.noteorganizerproject.notes.model.Note;
import com.refactoring.noteorganizerproject.utils.MigrationManager;

import java.util.ArrayList;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class NoteDaoImpl implements INoteDao {
    private final AppConfig appConfig = AppConfig.getInstance();
    private final NoteDAO noteDAO = appConfig.getDatabase().getNoteDao();
    private Disposable disposable = null;

    private int NO_MESSAGE = 0;

    // class vars
    private ArrayList<Note> notesList = new ArrayList<>();
    private List<Note> selectedNotes = new ArrayList<>();
    private Note note = new Note();

    @Override
    public Note getNote() { return note; }

    @Override
    public Note getNoteByPosition(int position) {
        return notesList == null ? new Note() : notesList.get(position);
    }

    @Override
    public Integer size() {
        return notesList == null ? 0 : notesList.size();
    }

    @Override
    public boolean wasSelected(Note note) {
        return selectedNotes.contains(note);
    }

    @Override
    public void selectNote(Note note) {
        if (wasSelected(note))
            selectedNotes.remove(note);
        else
            selectedNotes.add(note);
    }

    @Override
    public void sortNotes(BasePresenter presenter, Comparator comparator) {
        Collections.sort(notesList, comparator);
        presenter.notifyDatasetChanged(NO_MESSAGE);
    }

    @Override
    public void saveNote(Note note) {
        disposable = Completable.fromAction(() -> noteDAO.insert(note))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(() -> System.out.println("NOTE SAVED"),
                        Throwable::printStackTrace);
    }

    @Override
    public void updateNote(Note note) {
        disposable = Completable.fromAction(() -> noteDAO.update(note))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                        () -> System.out.println("NOTE UPDATED"),
                        Throwable::printStackTrace
                );
    }

    @Override
    public void deleteNote(Note note) {
        disposable = Completable.fromAction(() -> noteDAO.delete(note))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                        () -> System.out.println("NOTE DELETED"),
                        Throwable::printStackTrace
                );
    }

    @Override
    public void getFromDB(BasePresenter presenter) {
        disposable = noteDAO.getAll()
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        list -> {
                            notesList.clear();
                            notesList.addAll(list);
                            presenter.notifyDatasetChanged(NO_MESSAGE);
                        },
                        Throwable::printStackTrace
                );
    }

    @Override
    public void getNote(long id, BasePresenter presenter) {
        disposable = noteDAO.getById(id)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        foundNote -> {
                            this.note = foundNote;
                            presenter.notifyDatasetChanged(NO_MESSAGE);
                        },
                        Throwable::printStackTrace
                );
    }

    @Override
    public void deleteSelectedNote(BasePresenter presenter, Note note) {
        disposable = Completable.fromAction(() -> noteDAO.delete(note))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            selectedNotes.remove(note);
                            getFromDB(presenter);
                        },
                        Throwable::printStackTrace
                );
    }

    @Override
    public void deleteSelectedNotes(BasePresenter presenter) {
        for (Note note : selectedNotes)
            deleteSelectedNote(presenter, note);
    }

    @Override
    public void clearSelected() {
        selectedNotes.clear();
    }

    @Override
    public void search(BasePresenter presenter, String what) {
        disposable = noteDAO.getAll("%" + what + "%")
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        list -> {
                            notesList.clear();
                            notesList.addAll(list);
                            presenter.notifyDatasetChanged(NO_MESSAGE);
                        },
                        Throwable::printStackTrace
                );
    }

    @Override
    public void migrate(BasePresenter presenter) {
        disposable = noteDAO.getAll()
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        list -> {
                            System.out.println(list.size());
                            migration(list);
                            presenter.notifyDatasetChanged(R.string.migrated_hint);
                        },
                        Throwable::printStackTrace
                );
    }

    @Override
    public void migrate(Note note, BasePresenter presenter) {
        migration(note);
    }

    @Override
    public void migrateSelected(BasePresenter presenter) {
        migration(selectedNotes);
        selectedNotes.clear();
        presenter.notifyDatasetChanged(R.string.migrated_hint);
    }

    @Override
    public SharedPreferencesManager getAppSettings() {
        return AppConfig.getInstance().getAppSettings();
    }

    @Override
    public void unsubscribe() {
        disposable.dispose();
    }

    private void migration(Note note) {
        MigrationManager manager = new MigrationManager(getAppSettings());
        manager.saveToDir(note);
    }

    private void migration(List<Note> list) {
        MigrationManager manager = new MigrationManager(getAppSettings());
        for (Note note : list) {
            manager.saveToDir(note);
        }
    }

    @Override
    public void syncDataWithStorage() {
        MigrationManager manager = new MigrationManager(getAppSettings());
        List<Note> notesFromStorage = manager.getNotesFromStorage();
        disposable = noteDAO.getAll()
                .subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
                .subscribe(
                        list -> {
                            notesList.clear();
                            notesList.addAll(list);
                            addNotExisting(notesFromStorage);
                            System.out.println("SYNC COMPLETED");
                        },
                        Throwable::printStackTrace
                );
    }
    private void addNotExisting(List<Note> notesFromStorage) {
        boolean equals;
        for (Note noteFromStorage : notesFromStorage) {
            equals = false;
            for (Note noteFromList : notesList) {
                if (noteFromList.equals(noteFromStorage)) {
                    equals = true;
                    break;
                }
            }
            if (!equals) {
                saveNote(noteFromStorage);
                notesList.add(noteFromStorage);
            }
        }
    }




}

package rars.venus;

import rars.AssemblyException;
import rars.Globals;
import rars.RISCVprogram;
import rars.Settings;
import rars.riscv.hardware.RegisterFile;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.stream.Collectors;

	/*
Copyright (c) 2003-2010,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */

/**
 * Tabbed pane for the editor.  Each of its tabs represents an open file.
 *
 * @author Sanderson
 **/

public class EditTabbedPane extends JTabbedPane {
    EditPane editTab;
    MainPane mainPane;

    private VenusUI mainUI;
    private Editor editor;
    private FileOpener fileOpener;

    /**
     * Constructor for the EditTabbedPane class.
     **/

    public EditTabbedPane(VenusUI appFrame, Editor editor, MainPane mainPane) {
        super();
        this.mainUI = appFrame;
        this.editor = editor;
        this.fileOpener = new FileOpener(editor);
        this.mainPane = mainPane;
        this.editor.setEditTabbedPane(this);
        this.addChangeListener(
                new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        EditPane editPane = (EditPane) getSelectedComponent();
                        if (editPane != null) {
                            // New IF statement to permit free traversal of edit panes w/o invalidating
                            // assembly if assemble-all is selected.  DPS 9-Aug-2011
                            if (Globals.getSettings().getBooleanSetting(Settings.Bool.ASSEMBLE_ALL)) {
                                EditTabbedPane.this.updateTitles(editPane);
                            } else {
                                EditTabbedPane.this.updateTitlesAndMenuState(editPane);
                                EditTabbedPane.this.mainPane.getExecutePane().clearPane();
                            }
                            editPane.tellEditingComponentToRequestFocusInWindow();
                        }
                    }
                });
    }

    /**
     * The current EditPane representing a file.  Returns null if
     * no files open.
     *
     * @return the current editor pane
     */
    public EditPane getCurrentEditTab() {
        return (EditPane) this.getSelectedComponent();
    }

    /**
     * Select the specified EditPane to be the current tab.
     *
     * @param editPane The EditPane tab to become current.
     */
    public void setCurrentEditTab(EditPane editPane) {
        this.setSelectedComponent(editPane);
    }

    /**
     * If the given file is open in the tabbed pane, make it the
     * current tab.  If not opened, open it in a new tab and make
     * it the current tab.  If file is unable to be opened,
     * leave current tab as is.
     *
     * @param file File object for the desired file.
     * @return EditPane for the specified file, or null if file is unable to be opened in an EditPane
     */
    public EditPane getCurrentEditTabForFile(File file) {
        EditPane result = null;
        EditPane tab = getEditPaneForFile(file.getPath());
        if (tab != null) {
            if (tab != getCurrentEditTab()) {
                setCurrentEditTab(tab);
            }
            return tab;
        }
        // If no return yet, then file is not open.  Try to open it.
        if (openFile(file)) {
            result = getCurrentEditTab();
        }
        return result;
    }

    /**
     * Carries out all necessary operations to implement
     * the New operation from the File menu.
     */
    public void newFile() {
        EditPane editPane = new EditPane(this.mainUI);
        editPane.setSourceCode("", true);
        editPane.setShowLineNumbersEnabled(true);
        editPane.setFileStatus(FileStatus.NEW_NOT_EDITED);
        String name = editor.getNextDefaultFilename();
        editPane.setPathname(name);
        this.addTab(name, editPane);

        FileStatus.reset();
        FileStatus.setName(name);
        FileStatus.set(FileStatus.NEW_NOT_EDITED);

        RegisterFile.resetRegisters();
        mainUI.setReset(true);
        mainPane.getExecutePane().clearPane();
        mainPane.setSelectedComponent(this);
        editPane.displayCaretPosition(new Point(1, 1));
        this.setSelectedComponent(editPane);
        updateTitlesAndMenuState(editPane);
        editPane.tellEditingComponentToRequestFocusInWindow();
    }


    /**
     * Carries out all necessary operations to implement
     * the Open operation from the File menu.  This
     * begins with an Open File dialog.
     *
     * @return true if file was opened, false otherwise.
     */
    public boolean openFile() {
        return fileOpener.openFile();
    }

    /**
     * Carries out all necessary operations to open the
     * specified file in the editor.
     *
     * @return true if file was opened, false otherwise.
     */
    public boolean openFile(File file) {
        return fileOpener.openFile(file);
    }


    /**
     * Carries out all necessary operations to implement
     * the Close operation from the File menu.  May return
     * false, for instance when file has unsaved changes
     * and user selects Cancel from the warning dialog.
     *
     * @return true if file was closed, false otherwise.
     */
    public boolean closeCurrentFile() {
        EditPane editPane = getCurrentEditTab();
        if (editPane != null) {
            if (editsSavedOrAbandoned()) {
                this.remove(editPane);
                mainPane.getExecutePane().clearPane();
                mainPane.setSelectedComponent(this);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Carries out all necessary operations to implement
     * the Close All operation from the File menu.
     *
     * @return true if files closed, false otherwise.
     */
    public boolean closeAllFiles() {
        boolean result = true;
        boolean unsavedChanges = false;
        int tabCount = getTabCount();
        if (tabCount > 0) {
            mainPane.getExecutePane().clearPane();
            mainPane.setSelectedComponent(this);
            EditPane[] tabs = new EditPane[tabCount];
            for (int i = 0; i < tabCount; i++) {
                tabs[i] = (EditPane) getComponentAt(i);
                if (tabs[i].hasUnsavedEdits()) {
                    unsavedChanges = true;
                }
            }
            if (unsavedChanges) {
                switch (confirm("one or more files")) {
                    case JOptionPane.YES_OPTION:
                        boolean removedAll = true;
                        for (int i = 0; i < tabCount; i++) {
                            if (tabs[i].hasUnsavedEdits()) {
                                setSelectedComponent(tabs[i]);
                                boolean saved = saveCurrentFile();
                                if (saved) {
                                    this.remove(tabs[i]);
                                } else {
                                    removedAll = false;
                                }
                            } else {
                                this.remove(tabs[i]);
                            }
                        }
                        return removedAll;
                    case JOptionPane.NO_OPTION:
                        for (int i = 0; i < tabCount; i++) {
                            this.remove(tabs[i]);
                        }
                        return true;
                    case JOptionPane.CANCEL_OPTION:
                        return false;
                    default: // should never occur
                        return false;
                }
            } else {
                for (int i = 0; i < tabCount; i++) {
                    this.remove(tabs[i]);
                }
            }
        }
        return result;
    }

    /**
     * Saves file under existing name.  If no name, will invoke Save As.
     *
     * @return true if the file was actually saved.
     */
    public boolean saveCurrentFile() {
        EditPane editPane = getCurrentEditTab();
        if (saveFile(editPane)) {
            FileStatus.setSaved(true);
            FileStatus.setEdited(false);
            FileStatus.set(FileStatus.NOT_EDITED);
            editPane.setFileStatus(FileStatus.NOT_EDITED);
            updateTitlesAndMenuState(editPane);
            return true;
        }
        return false;
    }

    // Save file associatd with specified edit pane.
    // Returns true if save operation worked, else false.
    private boolean saveFile(EditPane editPane) {
        if (editPane != null) {
            if (editPane.isNew()) {
                File theFile = saveAsFile(editPane);
                if (theFile != null) {
                    editPane.setPathname(theFile.getPath());
                }
                return (theFile != null);
            }
            File theFile = new File(editPane.getPathname());
            try {
                BufferedWriter outFileStream = new BufferedWriter(new FileWriter(theFile));
                outFileStream.write(editPane.getSource(), 0, editPane.getSource().length());
                outFileStream.close();
            } catch (java.io.IOException c) {
                JOptionPane.showMessageDialog(null, "Save operation could not be completed due to an error:\n" + c,
                        "Save Operation Failed", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            return true;
        }
        return false;
    }


    /**
     * Pops up a dialog box to do "Save As" operation.  If necessary
     * an additional overwrite dialog is performed.
     *
     * @return true if the file was actually saved.
     */
    public boolean saveAsCurrentFile() {
        EditPane editPane = getCurrentEditTab();
        File theFile = saveAsFile(editPane);
        if (theFile != null) {
            FileStatus.setFile(theFile);
            FileStatus.setName(theFile.getPath());
            FileStatus.setSaved(true);
            FileStatus.setEdited(false);
            FileStatus.set(FileStatus.NOT_EDITED);
            editor.setCurrentSaveDirectory(theFile.getParent());
            editPane.setPathname(theFile.getPath());
            editPane.setFileStatus(FileStatus.NOT_EDITED);
            updateTitlesAndMenuState(editPane);
            return true;
        }
        return false;
    }

    // perform Save As for selected edit pane.  If the save is performed,
    // return its File object.  Otherwise return null.
    private File saveAsFile(EditPane editPane) {
        File theFile = null;
        if (editPane != null) {
            FileDialog saveDialog = null;
            // Set Save As dialog directory in a logical way.  If file in
            // edit pane had been previously saved, default to its directory.
            // If a new file (mipsN.asm), default to current save directory.
            // DPS 13-July-2011
            saveDialog = new FileDialog(mainUI, "Save As", FileDialog.SAVE);
            if (editPane.isNew()) {
                saveDialog.setDirectory(editor.getCurrentSaveDirectory());
            } else {
                File f = new File(editPane.getPathname());
                if (f.getParent() != null) {
                    saveDialog.setDirectory(f.getParent());
                } else {
                    saveDialog.setDirectory(editor.getCurrentSaveDirectory());
                }
            }
            String paneFile = editPane.getFilename();
            if (paneFile != null) saveDialog.setFile(paneFile);
            // end of 13-July-2011 code.

            saveDialog.show();
            if (saveDialog.getFiles().length != 0) {
                return null;
            }
            theFile = saveDialog.getFiles()[0];
            // Either file with selected name does not exist or user wants to 
            // overwrite it, so go for it!
            try {
                BufferedWriter outFileStream = new BufferedWriter(new FileWriter(theFile));
                outFileStream.write(editPane.getSource(), 0, editPane.getSource().length());
                outFileStream.close();
            } catch (java.io.IOException c) {
                JOptionPane.showMessageDialog(null, "Save As operation could not be completed due to an error:\n" + c,
                        "Save As Operation Failed", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }
        return theFile;
    }


    /**
     * Saves all files currently open in the editor.
     *
     * @return true if operation succeeded otherwise false.
     */
    public boolean saveAllFiles() {
        boolean result = false;
        int tabCount = getTabCount();
        if (tabCount > 0) {

            result = true;
            EditPane[] tabs = new EditPane[tabCount];
            EditPane savedPane = getCurrentEditTab();
            for (int i = 0; i < tabCount; i++) {
                tabs[i] = (EditPane) getComponentAt(i);
                if (tabs[i].hasUnsavedEdits()) {
                    setCurrentEditTab(tabs[i]);
                    if (saveFile(tabs[i])) {
                        tabs[i].setFileStatus(FileStatus.NOT_EDITED);
                        editor.setTitle(tabs[i].getPathname(), tabs[i].getFilename(), tabs[i].getFileStatus());
                    } else {
                        result = false;
                    }
                }
            }
            setCurrentEditTab(savedPane);
            if (result) {
                EditPane editPane = getCurrentEditTab();
                FileStatus.setSaved(true);
                FileStatus.setEdited(false);
                FileStatus.set(FileStatus.NOT_EDITED);
                editPane.setFileStatus(FileStatus.NOT_EDITED);
                updateTitlesAndMenuState(editPane);
            }
        }
        return result;
    }

    // TODO: this is too much of a hack, there needs to be a better way
    public String[] getOpenFilePaths() {
        int tabCount = getTabCount();
        String[] tabs = new String[tabCount];
        for (int i = 0; i < tabCount; i++) {
            tabs[i] = ((EditPane) getComponentAt(i)).getPathname();
        }
        return tabs;
    }
    /**
     * Remove the pane and update menu status
     */
    public void remove(EditPane editPane) {
        super.remove(editPane);
        editPane = getCurrentEditTab(); // is now next tab or null
        if (editPane == null) {
            FileStatus.set(FileStatus.NO_FILE);
            this.editor.setTitle("", "", FileStatus.NO_FILE);
            Globals.getGui().setMenuState(FileStatus.NO_FILE);
        } else {
            FileStatus.set(editPane.getFileStatus());
            updateTitlesAndMenuState(editPane);
        }
        // When last file is closed, menu is unable to respond to mnemonics
        // and accelerators.  Let's have it request focus so it may do so.
        if (getTabCount() == 0) mainUI.haveMenuRequestFocus();
    }


    // Handy little utility to update the title on the current tab and the frame title bar
    // and also to update the MARS menu state (controls which actions are enabled).
    private void updateTitlesAndMenuState(EditPane editPane) {
        editor.setTitle(editPane.getPathname(), editPane.getFilename(), editPane.getFileStatus());
        editPane.updateStaticFileStatus(); //  for legacy code that depends on the static FileStatus (pre 4.0)
        Globals.getGui().setMenuState(editPane.getFileStatus());
    }

    // Handy little utility to update the title on the current tab and the frame title bar
    // and also to update the MARS menu state (controls which actions are enabled).
    // DPS 9-Aug-2011
    private void updateTitles(EditPane editPane) {
        editor.setTitle(editPane.getPathname(), editPane.getFilename(), editPane.getFileStatus());
        boolean assembled = FileStatus.isAssembled();
        editPane.updateStaticFileStatus(); //  for legacy code that depends on the static FileStatus (pre 4.0)
        FileStatus.setAssembled(assembled);
    }

    /**
     * If there is an EditPane for the given file pathname, return it else return null.
     *
     * @param pathname Pathname for desired file
     * @return the EditPane for this file if it is open in the editor, or null if not.
     */
    public EditPane getEditPaneForFile(String pathname) {
        EditPane openPane = null;
        for (int i = 0; i < getTabCount(); i++) {
            EditPane pane = (EditPane) getComponentAt(i);
            if (pane.getPathname().equals(pathname)) {
                openPane = pane;
                break;
            }
        }
        return openPane;
    }

    /**
     * Check whether file has unsaved edits and, if so, check with user about saving them.
     *
     * @return true if no unsaved edits or if user chooses to save them or not; false
     * if there are unsaved edits and user cancels the operation.
     */
    public boolean editsSavedOrAbandoned() {
        EditPane currentPane = getCurrentEditTab();
        if (currentPane != null && currentPane.hasUnsavedEdits()) {
            switch (confirm(currentPane.getFilename())) {
                case JOptionPane.YES_OPTION:
                    return saveCurrentFile();
                case JOptionPane.NO_OPTION:
                    return true;
                case JOptionPane.CANCEL_OPTION:
                    return false;
                default: // should never occur
                    return false;
            }
        } else {
            return true;
        }
    }


    private int confirm(String name) {
        return JOptionPane.showConfirmDialog(mainUI,
                "Changes to " + name + " will be lost unless you save.  Do you wish to save all changes now?",
                "Save program changes?",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
    }


    private class FileOpener {
        private File mostRecentlyOpenedFile;
        private FileDialog fileChooser;
        private int fileFilterCount;
        private Editor theEditor;

        public FileOpener(Editor theEditor) {
            this.mostRecentlyOpenedFile = null;
            this.theEditor = theEditor;
            this.fileChooser = new FileDialog(mainUI, "", FileDialog.LOAD);
            fileChooser.setFile(Globals.fileExtensions.stream()
                    .map(s -> "*." + s)
                    .collect(Collectors.joining("|")));
        }

        /*
         * Launch a file chooser for name of file to open.  Return true if file opened, false otherwise
         */
        private boolean openFile() {
            // get name of file to be opened and load contents into text editing area.
            fileChooser.setDirectory(theEditor.getCurrentOpenDirectory());
            // Set default to previous file opened, if any.  This is useful in conjunction
            // with option to assemble file automatically upon opening.  File likely to have
            // been edited externally (e.g. by Mipster).
            if (Globals.getSettings().getBooleanSetting(Settings.Bool.ASSEMBLE_ON_OPEN) && mostRecentlyOpenedFile != null) {
                fileChooser.setFile(mostRecentlyOpenedFile.toString());
            }

            fileChooser.show();
            if (fileChooser.getFiles().length != 0) {
                File theFile = fileChooser.getFiles()[0];
                theEditor.setCurrentOpenDirectory(theFile.getParent());
                //theEditor.setCurrentSaveDirectory(theFile.getParent());// 13-July-2011 DPS.
                if (!openFile(theFile)) {
                    return false;
                }

                // possibly send this file right through to the assembler by firing Run->Assemble's
                // actionPerformed() method.
                if (theFile.canRead() && Globals.getSettings().getBooleanSetting(Settings.Bool.ASSEMBLE_ON_OPEN)) {
                    mainUI.getRunAssembleAction().actionPerformed(null);
                }
            }
            return true;
        }
      
       /*
        * Open the specified file.  Return true if file opened, false otherwise
        */

        private boolean openFile(File theFile) {
            try {
                theFile = theFile.getCanonicalFile();
            } catch (IOException ioe) {
                // nothing to do, theFile will keep current value
            }
            String currentFilePath = theFile.getPath();
            // If this file is currently already open, then simply select its tab
            EditPane editPane = getEditPaneForFile(currentFilePath);
            if (editPane != null) {
                setSelectedComponent(editPane);
                //updateTitlesAndMenuState(editPane);
                updateTitles(editPane);
                return false;
            } else {
                editPane = new EditPane(mainUI);
            }
            editPane.setPathname(currentFilePath);
            //FileStatus.reset();
            FileStatus.setName(currentFilePath);
            FileStatus.setFile(theFile);
            FileStatus.set(FileStatus.OPENING);// DPS 9-Aug-2011
            if (theFile.canRead()) {
                Globals.program = new RISCVprogram();
                try {
                    Globals.program.readSource(currentFilePath);
                } catch (AssemblyException pe) {
                }
                // DPS 1 Nov 2006.  Defined a StringBuffer to receive all file contents,
                // one line at a time, before adding to the Edit pane with one setText.
                // StringBuffer is preallocated to full filelength to eliminate dynamic
                // expansion as lines are added to it. Previously, each line was appended
                // to the Edit pane as it was read, way slower due to dynamic string alloc.
                StringBuffer fileContents = new StringBuffer((int) theFile.length());
                int lineNumber = 1;
                String line = Globals.program.getSourceLine(lineNumber++);
                while (line != null) {
                    fileContents.append(line + "\n");
                    line = Globals.program.getSourceLine(lineNumber++);
                }
                editPane.setSourceCode(fileContents.toString(), true);
                // The above operation generates an undoable edit, setting the initial
                // text area contents, that should not be seen as undoable by the Undo
                // action.  Let's get rid of it.
                editPane.discardAllUndoableEdits();
                editPane.setShowLineNumbersEnabled(true);
                editPane.setFileStatus(FileStatus.NOT_EDITED);

                addTab(editPane.getFilename(), editPane);
                setToolTipTextAt(indexOfComponent(editPane), editPane.getPathname());
                setSelectedComponent(editPane);
                FileStatus.setSaved(true);
                FileStatus.setEdited(false);
                FileStatus.set(FileStatus.NOT_EDITED);

                // If assemble-all, then allow opening of any file w/o invalidating assembly.
                // DPS 9-Aug-2011
                if (Globals.getSettings().getBooleanSetting(Settings.Bool.ASSEMBLE_ALL)) {
                    updateTitles(editPane);
                } else {// this was the original code...
                    updateTitlesAndMenuState(editPane);
                    mainPane.getExecutePane().clearPane();
                }

                mainPane.setSelectedComponent(EditTabbedPane.this);
                editPane.tellEditingComponentToRequestFocusInWindow();
                mostRecentlyOpenedFile = theFile;
            }
            return true;
        }
    }

}
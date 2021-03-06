// License: GPL. See LICENSE file for details.
package org.openstreetmap.josm.data.validation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.AbstractVisitor;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Utils;

/**
 * Parent class for all validation tests.
 * <p>
 * A test is a primitive visitor, so that it can access to all data to be
 * validated. These primitives are always visited in the same order: nodes
 * first, then ways.
 *
 * @author frsantos
 */
public class Test extends AbstractVisitor
{
    /** Name of the test */
    protected final String name;

    /** Description of the test */
    protected final String description;

    /** Whether this test is enabled. Enabled by default */
    public boolean enabled = true;

    /** The preferences check for validation */
    protected JCheckBox checkEnabled;

    /** The preferences check for validation on upload */
    protected JCheckBox checkBeforeUpload;

    /** Whether this test must check before upload. Enabled by default */
    public boolean testBeforeUpload = true;

    /** Whether this test is performing just before an upload */
    protected boolean isBeforeUpload;

    /** The list of errors */
    protected List<TestError> errors = new ArrayList<TestError>(30);

    /** Whether the test is run on a partial selection data */
    protected boolean partialSelection;

    /** the progress monitor to use */
    protected ProgressMonitor progressMonitor;
    
    /** the start time to compute elapsed time when test finishes */
    protected long startTime;
    
    /**
     * Constructor
     * @param name Name of the test
     * @param description Description of the test
     */
    public Test(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Constructor
     * @param name Name of the test
     */
    public Test(String name) {
        this(name, null);
    }

    /**
     * Initializes any global data used this tester.
     * @throws Exception When cannot initialize the test
     */
    public void initialize() throws Exception {
        this.startTime = -1;
    }

    /**
     * Start the test using a given progress monitor
     *
     * @param progressMonitor  the progress monitor
     */
    public void startTest(ProgressMonitor progressMonitor) {
        if (progressMonitor == null) {
            this.progressMonitor = NullProgressMonitor.INSTANCE;
        } else {
            this.progressMonitor = progressMonitor;
        }
        String startMessage = tr("Running test {0}", name);
        this.progressMonitor.beginTask(startMessage);
        Main.debug(startMessage);
        this.errors = new ArrayList<TestError>(30);
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Flag notifying that this test is run over a partial data selection
     * @param partialSelection Whether the test is on a partial selection data
     */
    public void setPartialSelection(boolean partialSelection) {
        this.partialSelection = partialSelection;
    }

    /**
     * Gets the validation errors accumulated until this moment.
     * @return The list of errors
     */
    public List<TestError> getErrors() {
        return errors;
    }

    /**
     * Notification of the end of the test. The tester may perform additional
     * actions and destroy the used structures.
     * <p>
     * If you override this method, don't forget to cleanup {@link #progressMonitor}
     * (most overrides call {@code super.endTest()} to do this).
     */
    public void endTest() {
        progressMonitor.finishTask();
        progressMonitor = null;
        if (startTime > 0) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            Main.info(tr("Test ''{0}'' completed in {1}", getName(), Utils.getDurationString(elapsedTime)));
        }
    }

    /**
     * Visits all primitives to be tested. These primitives are always visited
     * in the same order: nodes first, then ways.
     *
     * @param selection The primitives to be tested
     */
    public void visit(Collection<OsmPrimitive> selection) {
        progressMonitor.setTicksCount(selection.size());
        for (OsmPrimitive p : selection) {
            if (p.isUsable()) {
                p.accept(this);
            }
            progressMonitor.worked(1);
        }
    }

    @Override
    public void visit(Node n) {}

    @Override
    public void visit(Way w) {}

    @Override
    public void visit(Relation r) {}

    /**
     * Allow the tester to manage its own preferences
     * @param testPanel The panel to add any preferences component
     */
    public void addGui(JPanel testPanel) {
        checkEnabled = new JCheckBox(name, enabled);
        checkEnabled.setToolTipText(description);
        testPanel.add(checkEnabled, GBC.std());

        GBC a = GBC.eol();
        a.anchor = GridBagConstraints.EAST;
        checkBeforeUpload = new JCheckBox();
        checkBeforeUpload.setSelected(testBeforeUpload);
        testPanel.add(checkBeforeUpload, a);
    }

    /**
     * Called when the used submits the preferences
     */
    public boolean ok() {
        enabled = checkEnabled.isSelected();
        testBeforeUpload = checkBeforeUpload.isSelected();
        return false;
    }

    /**
     * Fixes the error with the appropriate command
     *
     * @param testError
     * @return The command to fix the error
     */
    public Command fixError(TestError testError) {
        return null;
    }

    /**
     * Returns true if the given error can be fixed automatically
     *
     * @param testError The error to check if can be fixed
     * @return true if the error can be fixed
     */
    public boolean isFixable(TestError testError) {
        return false;
    }

    /**
     * Returns true if this plugin must check the uploaded data before uploading
     * @return true if this plugin must check the uploaded data before uploading
     */
    public boolean testBeforeUpload() {
        return testBeforeUpload;
    }

    /**
     * Sets the flag that marks an upload check
     * @param isUpload if true, the test is before upload
     */
    public void setBeforeUpload(boolean isUpload) {
        this.isBeforeUpload = isUpload;
    }

    public String getName() {
        return name;
    }

    public boolean isCanceled() {
        return progressMonitor.isCanceled();
    }

    /**
     * Build a Delete command on all primitives that have not yet been deleted manually by user, or by another error fix.
     * If all primitives have already been deleted, null is returned.
     * @param primitives The primitives wanted for deletion
     * @return a Delete command on all primitives that have not yet been deleted, or null otherwise
     */
    protected final Command deletePrimitivesIfNeeded(Collection<? extends OsmPrimitive> primitives) {
        Collection<OsmPrimitive> primitivesToDelete = new ArrayList<OsmPrimitive>();
        for (OsmPrimitive p : primitives) {
            if (!p.isDeleted()) {
                primitivesToDelete.add(p);
            }
        }
        if (!primitivesToDelete.isEmpty()) {
            return DeleteCommand.delete(Main.main.getEditLayer(), primitivesToDelete);
        } else {
            return null;
        }
    }

    /**
     * Determines if the specified primitive denotes a building.
     * @param p The primitive to be tested
     * @return True if building key is set and different from no,entrance
     */
    protected static final boolean isBuilding(OsmPrimitive p) {
        String v = p.get("building");
        return v != null && !v.equals("no") && !v.equals("entrance");
    }
}

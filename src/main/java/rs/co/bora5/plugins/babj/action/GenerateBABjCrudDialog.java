package rs.co.bora5.plugins.babj.action;

import javax.swing.JComponent;

import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;

import rs.co.bora5.plugins.babj.model.BABjNaming;
import rs.co.bora5.plugins.babj.model.EntityModel;
import rs.co.bora5.plugins.babj.model.GenerationContext;
import rs.co.bora5.plugins.babj.model.OperaterTypeResolver;

/**
 * Configuration dialog for the BABj CRUD generator. Pre-fills every field from the selected entity
 * and the observed package layout, so the common case is just pressing OK.
 */
public class GenerateBABjCrudDialog extends DialogWrapper {

    private final EntityModel model;

    private final JBTextField basePackageField;
    private final JBTextField kTypeField;
    private final JBTextField roleField;
    private final JBTextField viewNameField;
    private final JBTextField routeField;
    private final JBTextField titleField;

    private final JBCheckBox dtoCheck = new JBCheckBox("DTO (projekcija)", true);
    private final JBCheckBox homeCheck = new JBCheckBox("Home (EJB servis)", true);
    private final JBCheckBox viewCheck = new JBCheckBox("View (grid)", true);
    private final JBCheckBox windowCheck = new JBCheckBox("EditWindow (prozor)", true);

    public GenerateBABjCrudDialog(@Nullable Project project, EntityModel model) {
        super(project);
        this.model = model;

        String entity = model.getSimpleName();
        basePackageField = new JBTextField(BABjNaming.basePackage(model.getPackageName()));
        kTypeField = new JBTextField(OperaterTypeResolver.resolve(project));
        roleField = new JBTextField("ADMIN");
        viewNameField = new JBTextField(entity + "View");
        routeField = new JBTextField(BABjNaming.decapitalize(entity) + "View");
        titleField = new JBTextField(BABjNaming.label(entity));

        setTitle("BABj CRUD generator — " + entity);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Osnovni paket:", basePackageField)
                .addLabeledComponent("Operater (K) tip:", kTypeField)
                .addLabeledComponent("Rola (Roles.___):", roleField)
                .addLabeledComponent("Naziv View klase:", viewNameField)
                .addLabeledComponent("Ruta:", routeField)
                .addLabeledComponent("Naslov stranice:", titleField)
                .addSeparator()
                .addComponent(dtoCheck)
                .addComponent(homeCheck)
                .addComponent(viewCheck)
                .addComponent(windowCheck)
                .getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (basePackageField.getText().isBlank()) {
            return new ValidationInfo("Osnovni paket je obavezan.", basePackageField);
        }
        if (kTypeField.getText().isBlank()) {
            return new ValidationInfo("Operater (K) tip je obavezan.", kTypeField);
        }
        if (viewCheck.isSelected() && viewNameField.getText().isBlank()) {
            return new ValidationInfo("Naziv View klase je obavezan.", viewNameField);
        }
        if (!dtoCheck.isSelected() && !homeCheck.isSelected()
                && !viewCheck.isSelected() && !windowCheck.isSelected()) {
            return new ValidationInfo("Izaberite bar jedan artefakt.", dtoCheck);
        }
        return null;
    }

    public GenerationContext buildContext() {
        return new GenerationContext(
                basePackageField.getText().trim(),
                model.getSimpleName(),
                kTypeField.getText().trim(),
                roleField.getText().trim(),
                viewNameField.getText().trim(),
                routeField.getText().trim(),
                titleField.getText().trim(),
                model.getFields(),
                dtoCheck.isSelected(),
                homeCheck.isSelected(),
                viewCheck.isSelected(),
                windowCheck.isSelected());
    }
}

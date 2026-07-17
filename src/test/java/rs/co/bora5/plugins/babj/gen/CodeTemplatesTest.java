package rs.co.bora5.plugins.babj.gen;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import rs.co.bora5.plugins.babj.model.BABjField;
import rs.co.bora5.plugins.babj.model.EntityModel;
import rs.co.bora5.plugins.babj.model.GenerationContext;

public class CodeTemplatesTest {

    @Test
    public void wiresCsvImportIntoView() {
        GenerationContext context = context(false, true, false,
                EntityModel.AttachmentSupport.none(), false);

        String source = CodeTemplates.view(context);

        assertTrue(source.contains("@EnableImport"));
        assertTrue(source.contains("@ImportWindow"));
        assertTrue(source.contains("ImportOrderCsvWindow importWindow"));
    }

    @Test
    public void generatesTypedFileSystemAttachmentSupport() {
        EntityModel.AttachmentSupport support = new EntityModel.AttachmentSupport(
                EntityModel.AttachmentKind.FILE_SYSTEM, "OrderAttachment",
                "example.model.OrderAttachment");
        GenerationContext context = context(false, false, true, support, false);

        String source = CodeTemplates.view(context);

        assertTrue(source.contains(
                "implements MultiFileSystemAttachmentViewInterface<Order, OrderAttachment, User>"));
        assertTrue(source.contains(
                "CustomMultiFileSystemInputField<Order, OrderAttachment, User> attachments"));
    }

    @Test
    public void generatesSettingsAdministrationQuartet() {
        GenerationContext context = context(true, false, false,
                EntityModel.AttachmentSupport.none(), false);

        assertTrue(CodeTemplates.dto(context).contains("extends AbstractSettingsDTO<SystemSettings>"));
        assertTrue(CodeTemplates.home(context).contains(
                "extends AbstractSettingsHome<SystemSettings, SystemSettingsDTO>"));
        assertTrue(CodeTemplates.view(context).contains("extends GenericSettingsView<"));
        assertTrue(CodeTemplates.window(context).contains("extends GenericSettingsWindow<"));
    }

    @Test
    public void messagingAgentFiltersAndBroadcastsEntityEvents() {
        GenerationContext context = context(false, false, false,
                EntityModel.AttachmentSupport.none(), true);

        String source = CodeTemplates.messagingAgent(context);

        assertTrue(source.contains("event instanceof EntityEvent entityEvent"));
        assertTrue(source.contains("entityEvent.getEntity() instanceof Order entity"));
        assertTrue(source.contains("getContext().broadcast"));
    }

    private static GenerationContext context(boolean settings, boolean csv, boolean attachments,
                                             EntityModel.AttachmentSupport attachmentSupport,
                                             boolean messaging) {
        String entity = settings ? "SystemSettings" : "Order";
        return new GenerationContext(
                "example", entity, "User", "Roles", "example.utils.Roles", List.of("ADMIN"),
                entity + "View", entity.toLowerCase(), entity,
                List.of(new BABjField("name", BABjField.Kind.SIMPLE, "String", null, null)),
                true, true, true, true, true, false, "", csv, false, false,
                attachments, attachmentSupport, messaging, settings);
    }
}

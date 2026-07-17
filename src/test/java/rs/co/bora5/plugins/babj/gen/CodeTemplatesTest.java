package rs.co.bora5.plugins.babj.gen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import rs.co.bora5.plugins.babj.model.BABjField;
import rs.co.bora5.plugins.babj.model.EntityModel;
import rs.co.bora5.plugins.babj.model.GenerationContext;

public class CodeTemplatesTest {

    @Test
    public void wiresCsvImportIntoView() {
        GenerationContext context = context(true, false,
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
        GenerationContext context = context(false, true, support, false);

        String source = CodeTemplates.view(context);

        assertTrue(source.contains(
                "implements MultiFileSystemAttachmentViewInterface<Order, OrderAttachment, User>"));
        assertTrue(source.contains(
                "CustomMultiFileSystemInputField<Order, OrderAttachment, User> attachments"));
    }

    @Test
    public void messagingAgentFiltersAndBroadcastsEntityEvents() {
        GenerationContext context = context(false, false,
                EntityModel.AttachmentSupport.none(), true);

        String source = CodeTemplates.messagingAgent(context);

        assertTrue(source.contains("event instanceof EntityEvent entityEvent"));
        assertTrue(source.contains("entityEvent.getEntity() instanceof Order entity"));
        assertTrue(source.contains("getContext().broadcast"));
    }

    @Test
    public void flattensAssociationsButKeepsEnumsTypedAcrossGeneratedArtifacts() {
        List<BABjField> fields = List.of(
                new BABjField("customer", BABjField.Kind.ASSOCIATION, "Customer",
                        "example.model.Customer", "username"),
                new BABjField("status", BABjField.Kind.ENUM, "OrderStatus",
                        "example.model.OrderStatus", null));
        GenerationContext context = context(false, false,
                EntityModel.AttachmentSupport.none(), false, fields);

        String dto = CodeTemplates.dto(context);
        String home = CodeTemplates.home(context);
        String window = CodeTemplates.window(context);

        assertTrue(dto.contains("private String customer;"));
        assertTrue(dto.contains("private OrderStatus status;"));
        assertTrue(dto.contains("OrderDTO(Long id, String customer, OrderStatus status)"));
        assertFalse(dto.contains("import example.model.Customer;"));
        assertTrue(dto.contains("import example.model.OrderStatus;"));

        assertTrue(home.contains(
                "(x.id, customer.username, x.status)"));
        assertTrue(home.contains("LEFT JOIN x.customer customer"));

        assertTrue(window.contains("ComboBox<Customer> cbCustomer"));
        assertTrue(window.contains(
                "createSimpleComboBox(\"Customer:\", customerEJB, \"username\")"));
        assertTrue(window.contains("ComboBox<OrderStatus> cbStatus"));
        assertTrue(window.contains("cbStatus.setItems(OrderStatus.values());"));
    }

    private static GenerationContext context(boolean csv, boolean attachments,
                                             EntityModel.AttachmentSupport attachmentSupport,
                                             boolean messaging) {
        return context(csv, attachments, attachmentSupport, messaging,
                List.of(new BABjField("name", BABjField.Kind.SIMPLE, "String", null, null)));
    }

    private static GenerationContext context(boolean csv, boolean attachments,
                                             EntityModel.AttachmentSupport attachmentSupport,
                                             boolean messaging, List<BABjField> fields) {
        String entity = "Order";
        return new GenerationContext(
                "example", entity, "User", "Roles", "example.utils.Roles", List.of("ADMIN"),
                entity + "View", entity.toLowerCase(), entity,
                fields,
                true, true, true, true, true, false, "", csv, false, false,
                attachments, attachmentSupport, messaging, false);
    }
}

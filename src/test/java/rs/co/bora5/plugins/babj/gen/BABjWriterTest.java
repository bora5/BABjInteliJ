package rs.co.bora5.plugins.babj.gen;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class BABjWriterTest extends LightJavaCodeInsightFixtureTestCase {

    public void testRequiresExplicitOverwriteAndReportsReplacement() throws Exception {
        PsiDirectory directory = PsiManager.getInstance(getProject()).findDirectory(
                myFixture.getTempDirFixture().findOrCreateDir("src/example"));
        assertNotNull(directory);

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            BABjWriter.WriteResult created = BABjWriter.writeJava(
                    getProject(), directory, "OrderDTO.java", "class OrderDTO {}", false);
            assertNotNull(created.file());
            assertFalse(created.replaced());

            BABjWriter.WriteResult skipped = BABjWriter.writeJava(
                    getProject(), directory, "OrderDTO.java", "class OrderDTO { int changed; }", false);
            assertNull(skipped.file());
            assertFalse(skipped.replaced());
            assertFalse(directory.findFile("OrderDTO.java").getText().contains("changed"));

            BABjWriter.WriteResult recreated = BABjWriter.writeJava(
                    getProject(), directory, "OrderDTO.java", "class OrderDTO { int changed; }", true);
            assertNotNull(recreated.file());
            assertTrue(recreated.replaced());
            assertTrue(recreated.file().getText().contains("changed"));
        });
    }
}

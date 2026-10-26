package ir.madar.app;
import static org.junit.Assert.*;
import org.junit.Test;
public class PersianFormatterTest {
 @Test public void changesColloquialWords() {
   assertEquals("می‌خواهم یک کار را انجام دهم.", PersianFormatter.clean("میخوام یه کار رو بزنم"));
 }
 @Test public void keepsPersianWriting() {
   assertEquals("یک خانه.", PersianFormatter.clean("يه خونه"));
 }
 @Test public void splitsOnlyExplicitSeparators() {
   assertEquals(2, PersianFormatter.splitTasks("خرید؛ تماس").length);
   assertEquals(1, PersianFormatter.splitTasks("جلسه با مدیر و همکار").length);
   assertEquals(2, PersianFormatter.splitTasks("به مدیر زنگ بزن کار بعدی خرید انجام بده").length);
 }
}

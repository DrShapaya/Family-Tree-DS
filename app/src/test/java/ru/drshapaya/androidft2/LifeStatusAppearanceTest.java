package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import java.io.StringWriter;
import org.junit.Test;

public class LifeStatusAppearanceTest {
    @Test public void oldAndMalformedPreferencesFallBackWithoutInvalidIconsOrColors() {
        String defaults=new LifeStatusAppearance().encode();
        assertEquals(defaults,LifeStatusAppearance.decode(null).encode());
        assertEquals(defaults,LifeStatusAppearance.decode("broken").encode());
        assertEquals(defaults,LifeStatusAppearance.decode("skull|heart|unknown|red|#12|transparent|bad").encode());
        LifeStatusAppearance preferences=new LifeStatusAppearance();
        preferences.setColor(PersonLifeStatus.LIVING,"#bB1122");
        assertEquals("#BB1122",preferences.livingColor);
        preferences.setIcon(PersonLifeStatus.DECEASED,"sprout");
        assertEquals("candle",preferences.deceasedIcon);
    }

    @Test public void copyingAndHistoryKeepIndependentPreferences() {
        TreeState before=new TreeState(),after=TreeStateCopier.copy(before);
        after.lifeStatusAppearance.livingIcon="lotus";
        after.lifeStatusAppearance.deceasedColor="#36393D";
        after.lifeStatusAppearance.style="soft";
        assertEquals("heart",before.lifeStatusAppearance.livingIcon);
        TreeDeltaCommand delta=TreeDeltaCommand.between(before,after,"Иконки");
        assertFalse(delta.isEmpty());
        String expected=after.lifeStatusAppearance.encode();
        delta.undo(after); assertEquals(before.lifeStatusAppearance.encode(),after.lifeStatusAppearance.encode());
        delta.redo(after); assertEquals(expected,after.lifeStatusAppearance.encode());
    }

    @Test public void xmlPreservesAllThreeIconsAndColorsAndBackground() throws Exception {
        TreeState state=new TreeState();
        state.lifeStatusAppearance.livingIcon="sprout";
        state.lifeStatusAppearance.deceasedIcon="ribbon";
        state.lifeStatusAppearance.unknownIcon="ellipsis";
        state.lifeStatusAppearance.livingColor="#117744";
        state.lifeStatusAppearance.deceasedColor="#222222";
        state.lifeStatusAppearance.unknownColor="#886699";
        state.lifeStatusAppearance.style="plain";
        StringWriter out=new StringWriter(); TreeReadableExport.xml(state,out,p -> "");
        assertEquals(state.lifeStatusAppearance.encode(),TreeXmlReader.read(out.toString()).lifeStatusAppearance.encode());
    }
}

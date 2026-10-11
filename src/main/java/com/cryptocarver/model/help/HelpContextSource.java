package com.cryptocarver.model.help;

import java.util.Locale;
import java.util.function.Consumer;

/** Observable source for the current public help context, shared by presentations. */
public interface HelpContextSource {
    HelpContext current();
    void addListener(Consumer<HelpContext> listener);
    void removeListener(Consumer<HelpContext> listener);
    void setOperation(String operationId);
    void setAction(String actionId);
    void setLocale(Locale locale);
    void setField(String fieldId);
    void setSelection(HelpContext.Selection selection, HelpContext.PublicSelection value);
    void replace(HelpContext context);
}

/*******************************************************************************
 * Copyright (c) 2010 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.scout.rt.client.ui.form.fields.mailfield;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import javax.mail.internet.MimeMessage;

import org.eclipse.scout.commons.annotations.ClassId;
import org.eclipse.scout.commons.annotations.ConfigOperation;
import org.eclipse.scout.commons.annotations.ConfigProperty;
import org.eclipse.scout.commons.annotations.Order;
import org.eclipse.scout.commons.exception.ProcessingException;
import org.eclipse.scout.commons.logger.IScoutLogger;
import org.eclipse.scout.commons.logger.ScoutLogManager;
import org.eclipse.scout.rt.client.CurrentControlTracker;
import org.eclipse.scout.rt.client.CurrentControlTracker.ContextInfo;
import org.eclipse.scout.rt.client.extension.ui.form.fields.IFormFieldExtension;
import org.eclipse.scout.rt.client.extension.ui.form.fields.mailfield.IMailFieldExtension;
import org.eclipse.scout.rt.client.extension.ui.form.fields.mailfield.MailFieldChains.MailFieldAttachementActionChain;
import org.eclipse.scout.rt.client.extension.ui.form.fields.mailfield.MailFieldChains.MailFieldHyperlinkActionChain;
import org.eclipse.scout.rt.client.ui.form.fields.AbstractFormField;
import org.eclipse.scout.rt.client.ui.form.fields.AbstractValueField;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.exception.ExceptionHandler;
import org.eclipse.scout.rt.shared.ScoutTexts;

/**
 * @version 3.x
 */
@ClassId("35e1fd57-3c86-4c99-92ca-188c3c2dedde")
public abstract class AbstractMailField extends AbstractValueField<MimeMessage> implements IMailField {
  private static final IScoutLogger LOG = ScoutLogManager.getLogger(AbstractMailField.class);
  private IMailFieldUIFacade m_uiFacade;
  private boolean m_mailEditor;
  private boolean m_scrollBarEnabled;

  public AbstractMailField() {
    this(true);
  }

  public AbstractMailField(boolean callInitializer) {
    super(callInitializer);
  }

  /*
   * Configuration
   */
  @ConfigProperty(ConfigProperty.BOOLEAN)
  @Order(240)
  protected boolean getConfiguredMailEditor() {
    return false;
  }

  @ConfigProperty(ConfigProperty.BOOLEAN)
  @Order(250)
  protected boolean getConfiguredScrollBarEnabled() {
    return false;
  }

  @ConfigProperty(ConfigProperty.TEXT)
  @Order(260)
  protected String getConfiguredLabelFrom() {
    return ScoutTexts.get("EmailFrom");
  }

  @ConfigProperty(ConfigProperty.TEXT)
  @Order(270)
  protected String getConfiguredLabelTo() {
    return ScoutTexts.get("EmailTo");
  }

  @ConfigProperty(ConfigProperty.TEXT)
  @Order(280)
  protected String getConfiguredLabelCc() {
    return ScoutTexts.get("EmailCc");
  }

  @ConfigProperty(ConfigProperty.TEXT)
  @Order(290)
  protected String getConfiguredLabelSubject() {
    return ScoutTexts.get("EmailSubject");
  }

  @ConfigProperty(ConfigProperty.TEXT)
  @Order(300)
  protected String getConfiguredLabelSent() {
    return ScoutTexts.get("EmailSent");
  }

  @Override
  @Order(210)
  @ConfigProperty(ConfigProperty.BOOLEAN)
  protected boolean getConfiguredAutoAddDefaultMenus() {
    return false;
  }

  /**
   * @param url
   * @param path
   *          {@link URL#getPath()}
   * @param local
   *          true if the url is not a valid external url but a local model url
   *          (http://local/...)
   */
  @ConfigOperation
  @Order(230)
  protected void execAttachementAction(File file) throws ProcessingException {
  }

  /**
   * @param url
   * @param path
   *          {@link URL#getPath()}
   * @param local
   *          true if the url is not a valid external url but a local model url
   *          (http://local/...)
   */
  @ConfigOperation
  @Order(240)
  protected void execHyperlinkAction(URL url, String path, boolean local) throws ProcessingException {
  }

  /**
   * Called when an app link has been clicked.
   * <p>
   * Subclasses can override this method. The default does nothing.
   */
  @ConfigOperation
  @Order(10)
  protected void execAppLinkAction(String ref) throws ProcessingException {
    //FIXME CGU remove this code when execpHyperlinkAction has been removed
    URL url = null;
    boolean local = false;
    if (ref != null) {
      try {
        url = new URL(ref);
        local = "local".equals(url.getHost());
      }
      catch (MalformedURLException e) {
        LOG.error("", e);
      }
    }
    execHyperlinkAction(url, ref, local);
  }

  @Override
  protected void initConfig() {
    m_uiFacade = BEANS.get(CurrentControlTracker.class).newProxy(new P_UIFacade(), ContextInfo.copyCurrent());
    super.initConfig();
    m_mailEditor = getConfiguredMailEditor();
    m_scrollBarEnabled = getConfiguredScrollBarEnabled();
    setLabelFrom(getConfiguredLabelFrom());
    setLabelTo(getConfiguredLabelTo());
    setLabelCc(getConfiguredLabelCc());
    setLabelSent(getConfiguredLabelSent());
    setLabelSubject(getConfiguredLabelSubject());
  }

  @Override
  public void setLabelFrom(String fromLabel) {
    propertySupport.setPropertyString(PROP_LABEL_FROM, fromLabel);
  }

  @Override
  public String getLabelFrom() {
    return propertySupport.getPropertyString(PROP_LABEL_FROM);
  }

  @Override
  public void setLabelTo(String toLabel) {
    propertySupport.setPropertyString(PROP_LABEL_TO, toLabel);
  }

  @Override
  public String getLabelTo() {
    return propertySupport.getPropertyString(PROP_LABEL_TO);
  }

  @Override
  public void setLabelCc(String ccLabel) {
    propertySupport.setPropertyString(PROP_LABEL_CC, ccLabel);
  }

  @Override
  public String getLabelCc() {
    return propertySupport.getPropertyString(PROP_LABEL_CC);
  }

  @Override
  public void setLabelSubject(String subjectLabel) {
    propertySupport.setPropertyString(PROP_LABEL_SUBJECT, subjectLabel);

  }

  @Override
  public String getLabelSubject() {
    return propertySupport.getPropertyString(PROP_LABEL_SUBJECT);
  }

  @Override
  public void setLabelSent(String sentLabel) {
    propertySupport.setProperty(PROP_LABEL_SENT, sentLabel);
  }

  @Override
  public String getLabelSent() {
    return propertySupport.getPropertyString(PROP_LABEL_SENT);
  }

  @Override
  public IMailFieldUIFacade getUIFacade() {
    return m_uiFacade;
  }

  @Override
  public boolean isMailEditor() {
    return m_mailEditor;
  }

  @Override
  public boolean isScrollBarEnabled() {
    return m_scrollBarEnabled;
  }

  public void doAttachementAction(File file) throws ProcessingException {
    interceptAttachementAction(file);
  }

  @Override
  public void doAppLinkAction(String ref) throws ProcessingException {
    interceptAppLinkAction(ref);
  }

  private class P_UIFacade implements IMailFieldUIFacade {
    @Override
    public boolean setMailFromUI(MimeMessage message) {
      if (isMailEditor()) {
        setValue(message);
      }
      return true;
    }

    @Override
    public void fireAttachementActionFromUI(File file) {
      try {
        doAttachementAction(file);
      }
      catch (ProcessingException e) {
        BEANS.get(ExceptionHandler.class).handle(e);
      }
    }

    @Override
    public void fireAppLinkActionFromUI(String ref) {
      try {
        doAppLinkAction(ref);
      }
      catch (ProcessingException e) {
        BEANS.get(ExceptionHandler.class).handle(e);
      }
    }

  }

  protected final void interceptAppLinkAction(String ref) throws ProcessingException {
    List<? extends IFormFieldExtension<? extends AbstractFormField>> extensions = getAllExtensions();
    MailFieldHyperlinkActionChain chain = new MailFieldHyperlinkActionChain(extensions);
    chain.execHyperlinkAction(ref);
  }

  protected final void interceptAttachementAction(File file) throws ProcessingException {
    List<? extends IFormFieldExtension<? extends AbstractFormField>> extensions = getAllExtensions();
    MailFieldAttachementActionChain chain = new MailFieldAttachementActionChain(extensions);
    chain.execAttachementAction(file);
  }

  protected static class LocalMailFieldExtension<OWNER extends AbstractMailField> extends LocalValueFieldExtension<MimeMessage, OWNER> implements IMailFieldExtension<OWNER> {

    public LocalMailFieldExtension(OWNER owner) {
      super(owner);
    }

    @Override
    public void execHyperlinkAction(MailFieldHyperlinkActionChain chain, String ref) throws ProcessingException {
      getOwner().execAppLinkAction(ref);
    }

    @Override
    public void execAttachementAction(MailFieldAttachementActionChain chain, File file) throws ProcessingException {
      getOwner().execAttachementAction(file);
    }
  }

  @Override
  protected IMailFieldExtension<? extends AbstractMailField> createLocalExtension() {
    return new LocalMailFieldExtension<AbstractMailField>(this);
  }

}

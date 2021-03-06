/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.faces.config.processor;

import static java.text.MessageFormat.format;
import static java.util.logging.Level.FINE;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import javax.faces.application.Application;
import javax.faces.context.FacesContext;
import javax.faces.validator.BeanValidator;
import javax.faces.validator.FacesValidator;
import javax.faces.validator.Validator;
import javax.servlet.ServletContext;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.faces.config.ConfigurationException;
import com.sun.faces.config.Verifier;
import com.sun.faces.config.manager.documents.DocumentInfo;
import com.sun.faces.util.FacesLogger;

/**
 * <p>
 * This <code>ConfigProcessor</code> handles all elements defined under
 * <code>/faces-config/valiator</code>.
 * </p>
 */
public class ValidatorConfigProcessor extends AbstractConfigProcessor {

    private static final Logger LOGGER = FacesLogger.CONFIG.getLogger();

    /**
     * <p>
     * /faces-config/validator
     * </p>
     */
    private static final String VALIDATOR = "validator";

    /**
     * <p>
     * /faces-config/component/validator-id
     * </p>
     */
    private static final String VALIDATOR_ID = "validator-id";

    /**
     * <p>
     * /faces-config/component/validator-class
     * </p>
     */
    private static final String VALIDATOR_CLASS = "validator-class";

    // -------------------------------------------- Methods from ConfigProcessor

    /**
     * @see ConfigProcessor#process(javax.servlet.ServletContext,com.sun.faces.config.manager.documents.DocumentInfo[])
     */
    @Override
    public void process(ServletContext servletContext, FacesContext facesContext, DocumentInfo[] documentInfos) throws Exception {

        // Process annotated Validators first as Validators configured
        // via config files take precedence
        processAnnotations(facesContext, FacesValidator.class);

        for (int i = 0; i < documentInfos.length; i++) {
            if (LOGGER.isLoggable(FINE)) {
                LOGGER.log(FINE, format("Processing validator elements for document: ''{0}''", documentInfos[i].getSourceURI()));
            }
            
            Document document = documentInfos[i].getDocument();
            String namespace = document.getDocumentElement().getNamespaceURI();
            NodeList validators = document.getDocumentElement().getElementsByTagNameNS(namespace, VALIDATOR);
            
            if (validators != null && validators.getLength() > 0) {
                addValidators(facesContext, validators, namespace);
            }
        }
        
        processDefaultValidatorIds();
    }

    // --------------------------------------------------------- Private Methods

    private void processDefaultValidatorIds() {

        Application app = getApplication();
        Map<String, String> defaultValidatorInfo = app.getDefaultValidatorInfo();
        for (Map.Entry<String, String> info : defaultValidatorInfo.entrySet()) {
            String defaultValidatorId = info.getKey();
            boolean found = false;
            for (Iterator<String> registered = app.getValidatorIds(); registered.hasNext();) {
                if (defaultValidatorId.equals(registered.next())) {
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                throw new ConfigurationException(format("Default validator ''{0}'' does not reference a registered validator.", defaultValidatorId));
            }
        }

    }

    private void addValidators(FacesContext facesContext, NodeList validators, String namespace) throws XPathExpressionException {

        Application application = getApplication();
        Verifier verifier = Verifier.getCurrentInstance();
        for (int i = 0, size = validators.getLength(); i < size; i++) {
            Node validator = validators.item(i);

            NodeList children = ((Element) validator).getElementsByTagNameNS(namespace, "*");
            String validatorId = null;
            String validatorClass = null;
            
            for (int c = 0, csize = children.getLength(); c < csize; c++) {
                Node n = children.item(c);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    switch (n.getLocalName()) {
                    case VALIDATOR_ID:
                        validatorId = getNodeText(n);
                        break;
                    case VALIDATOR_CLASS:
                        validatorClass = getNodeText(n);
                        break;
                    }
                }
            }

            if (validatorId != null && validatorClass != null) {
                if (LOGGER.isLoggable(FINE)) {
                    LOGGER.log(FINE, format("Calling Application.addValidator({0},{1})", validatorId, validatorClass));
                }

                boolean doAdd = true;
                if (validatorId.equals(BeanValidator.VALIDATOR_ID)) {
                    doAdd = ApplicationConfigProcessor.isBeanValidatorAvailable(facesContext);
                }

                if (doAdd) {
                    if (verifier != null) {
                        verifier.validateObject(Verifier.ObjectType.VALIDATOR, validatorClass, Validator.class);
                    }
                    application.addValidator(validatorId, validatorClass);
                }
            }

        }
    }

}

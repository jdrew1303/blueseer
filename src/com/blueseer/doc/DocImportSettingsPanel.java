/*
The MIT License (MIT)

Copyright (c) Terry Evans Vaughn

All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.blueseer.doc;

import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;

/**
 * One-time local-LLM runtime setup for agentic document import (epic
 * docs/epics/agentic-document-import.md Sec 7.3, DOC-16/17) - a standalone
 * screen registered as its own {@code menu_mstr.menu_panel} row (same
 * convention as {@link com.blueseer.ing.NutritionCompletenessPanel}),
 * rather than a new tab wedged into the generated {@code SystemControl}
 * GroupLayout. Set once by whoever installs/supports a given customer;
 * shop staff never see this screen and the Import buttons elsewhere just
 * stay hidden when nothing here is configured.
 */
public class DocImportSettingsPanel extends JPanel {

    private final JComboBox<String> ddProvider = new JComboBox<>(new String[]{"LMSTUDIO", "OLLAMA"});
    private final JTextField tbBaseUrl = new JTextField(30);
    private final JTextField tbModel = new JTextField(30);
    private final JCheckBox cbEnabled = new JCheckBox("Document import enabled");
    private final JButton btSave = new JButton("Save");
    private final JLabel lblStatus = new JLabel(" ");

    public DocImportSettingsPanel() {
        // A bare MigLayout directly on `this` left the fields floating on the
        // app's configurable background color with nothing visually tying them
        // together - every other screen groups its fields inside a bordered
        // card (e.g. RecvMaint's "Receiver Maintenance" TitledBorder box), so
        // this does the same instead of introducing a one-off look.
        setLayout(new BorderLayout());
        JPanel card = new JPanel(new MigLayout("insets 12, wrap 2", "[right]8[grow, fill]"));
        card.setBorder(BorderFactory.createTitledBorder("Document Import Settings"));
        add(card, BorderLayout.NORTH);

        card.add(new JLabel("Runtime"));
        card.add(ddProvider);
        card.add(new JLabel("Base URL"));
        card.add(tbBaseUrl);
        card.add(new JLabel("Model"));
        card.add(tbModel);
        card.add(new JLabel());
        card.add(cbEnabled);
        card.add(new JLabel());
        card.add(btSave);
        card.add(lblStatus, "span 2");

        ddProvider.addActionListener(e -> applyProviderDefault());
        btSave.addActionListener(e -> save());

        load();
    }

    private void applyProviderDefault() {
        if (!tbBaseUrl.getText().isBlank()) {
            return;
        }
        tbBaseUrl.setText("OLLAMA".equals(ddProvider.getSelectedItem()) ? "http://localhost:11434" : "http://localhost:1234");
    }

    private void load() {
        docData.llm_config cfg = docData.getLlmConfig();
        ddProvider.setSelectedItem(cfg.provider());
        tbBaseUrl.setText(cfg.baseurl());
        tbModel.setText(cfg.model());
        cbEnabled.setSelected(cfg.enabled());
    }

    private void save() {
        docData.llm_config cfg = new docData.llm_config(
                ddProvider.getSelectedItem().toString(), tbBaseUrl.getText().trim(), tbModel.getText().trim(), cbEnabled.isSelected());
        String[] result = docData.saveLlmConfig(cfg);
        lblStatus.setText(result.length > 1 ? result[1] : "");
    }
}

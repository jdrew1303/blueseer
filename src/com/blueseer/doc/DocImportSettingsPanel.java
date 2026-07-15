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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.FlowLayout;

/**
 * One-time local-LLM runtime setup for agentic document import (epic
 * docs/epics/agentic-document-import.md Sec 7.3, DOC-16/17) - a standalone
 * screen registered as its own {@code menu_mstr.menu_panel} row (same
 * convention as {@link com.blueseer.ing.NutritionCompletenessPanel}),
 * rather than a new tab wedged into the generated {@code SystemControl}
 * GroupLayout. Set once by whoever installs/supports a given customer;
 * shop staff never see this screen and the Import buttons elsewhere just
 * stay hidden when nothing here is configured.
 *
 * Has two independent model configs: the extraction model (required,
 * enabled/disabled explicitly) and an optional document-layout model (e.g.
 * granite-docling-258M) that only ever helps show where an extracted field
 * came from in the source document - leaving its model field blank turns
 * it off with no other behavior change, see docData.layout_llm_config.
 */
public class DocImportSettingsPanel extends JPanel {

    private final JComboBox<String> ddProvider = new JComboBox<>(new String[]{"LMSTUDIO", "OLLAMA"});
    private final JTextField tbBaseUrl = new JTextField(30);
    private final JTextField tbModel = new JTextField(30);
    private final JCheckBox cbEnabled = new JCheckBox("Scan to Import enabled");
    private final JComboBox<String> ddLayoutProvider = new JComboBox<>(new String[]{"LMSTUDIO", "OLLAMA"});
    private final JTextField tbLayoutBaseUrl = new JTextField(30);
    private final JTextField tbLayoutModel = new JTextField(30);
    private final JButton btSave = new JButton("Save");
    private final JLabel lblStatus = new JLabel(" ");

    public DocImportSettingsPanel() {
        // A bare MigLayout directly on `this` left the fields floating on the
        // app's configurable background color with nothing visually tying them
        // together - every other screen groups its fields inside a bordered
        // card (e.g. RecvMaint's "Receiver Maintenance" TitledBorder box), so
        // this does the same instead of introducing a one-off look.
        //
        // FlowLayout (not BorderLayout.NORTH) so the outer wrapper sizes to its
        // own preferred width instead of stretching full-width; CENTER (not
        // LEFT) to match every other screen's top-centered look (e.g.
        // RecvMaint's outer panel relies on JPanel's own default FlowLayout,
        // which is CENTER).
        setLayout(new FlowLayout(FlowLayout.CENTER));
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.add(buildExtractionCard());
        outer.add(Box.createVerticalStrut(10));
        outer.add(buildLayoutModelCard());
        add(outer);

        ddProvider.addActionListener(e -> applyProviderDefault(ddProvider, tbBaseUrl));
        ddLayoutProvider.addActionListener(e -> applyProviderDefault(ddLayoutProvider, tbLayoutBaseUrl));
        btSave.addActionListener(e -> save());

        load();
    }

    private JPanel buildExtractionCard() {
        JPanel card = new JPanel(new MigLayout("insets 12, wrap 2", "[right]8[]"));
        card.setBorder(BorderFactory.createTitledBorder("Scan to Import Settings"));

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
        return card;
    }

    private JPanel buildLayoutModelCard() {
        JPanel card = new JPanel(new MigLayout("insets 12, wrap 2", "[right]8[]"));
        card.setBorder(BorderFactory.createTitledBorder("Document Layout Model (optional)"));

        card.add(new JLabel("Leave the model field blank to skip this - Scan to Import works fine without it."), "span 2, wrap");
        card.add(new JLabel("Runtime"));
        card.add(ddLayoutProvider);
        card.add(new JLabel("Base URL"));
        card.add(tbLayoutBaseUrl);
        card.add(new JLabel("Model"));
        card.add(tbLayoutModel);
        return card;
    }

    private static void applyProviderDefault(JComboBox<String> providerCombo, JTextField baseUrlField) {
        if (!baseUrlField.getText().isBlank()) {
            return;
        }
        baseUrlField.setText("OLLAMA".equals(providerCombo.getSelectedItem()) ? "http://localhost:11434" : "http://localhost:1234");
    }

    private void load() {
        docData.llm_config cfg = docData.getLlmConfig();
        ddProvider.setSelectedItem(cfg.provider());
        tbBaseUrl.setText(cfg.baseurl());
        tbModel.setText(cfg.model());
        cbEnabled.setSelected(cfg.enabled());

        docData.layout_llm_config layoutCfg = docData.getLayoutLlmConfig();
        ddLayoutProvider.setSelectedItem(layoutCfg.provider());
        tbLayoutBaseUrl.setText(layoutCfg.baseurl());
        tbLayoutModel.setText(layoutCfg.model());
    }

    private void save() {
        docData.llm_config cfg = new docData.llm_config(
                ddProvider.getSelectedItem().toString(), tbBaseUrl.getText().trim(), tbModel.getText().trim(), cbEnabled.isSelected());
        String[] result = docData.saveLlmConfig(cfg);

        docData.layout_llm_config layoutCfg = new docData.layout_llm_config(
                ddLayoutProvider.getSelectedItem().toString(), tbLayoutBaseUrl.getText().trim(), tbLayoutModel.getText().trim());
        docData.saveLayoutLlmConfig(layoutCfg);

        lblStatus.setText(result.length > 1 ? result[1] : "");
    }
}

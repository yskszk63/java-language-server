'use strict';
import {window, workspace, ExtensionContext, Uri, ConfigurationChangeEvent, TextDocumentChangeEvent, Range, Position, TextDocument, TextEditor} from 'vscode';
import * as path from 'path';
import Parser = require('web-tree-sitter');
import { colorJava, Range as ColorRange } from './treeSitterColor';
import { loadStyles, decoration } from './textMate';

export async function activateTreeSitter(context: ExtensionContext) {
    let javaParser: Parser = null
	// Parse of all visible documents
	const trees: {[uri: string]: Parser.Tree} = {}
	async function open(editor: TextEditor) {
        if (editor.document.languageId != 'java') return
        if (javaParser == null) {
            const absolute = path.join(context.extensionPath, 'lib', 'tree-sitter-java.wasm');
			const wasm = path.relative(process.cwd(), absolute)
			const lang = await Parser.Language.load(wasm)
			javaParser = new Parser()
			javaParser.setLanguage(lang)
		}
		const t = javaParser.parse(editor.document.getText()) // TODO don't use getText, use Parser.Input
		trees[editor.document.uri.toString()] = t
		colorUri(editor.document.uri)
	}
	// NOTE: if you make this an async function, it seems to cause edit anomalies
	function edit(edit: TextDocumentChangeEvent) {
        if (edit.document.languageId != 'java') return
		updateTree(javaParser, edit)
		colorUri(edit.document.uri)
	}
	function updateTree(parser: Parser, edit: TextDocumentChangeEvent) {
		if (edit.contentChanges.length == 0) return
		const old = trees[edit.document.uri.toString()]
		for (const e of edit.contentChanges) {
			const startIndex = e.rangeOffset
			const oldEndIndex = e.rangeOffset + e.rangeLength
			const newEndIndex = e.rangeOffset + e.text.length
			const startPos = edit.document.positionAt(startIndex)
			const oldEndPos = edit.document.positionAt(oldEndIndex)
			const newEndPos = edit.document.positionAt(newEndIndex)
			const startPosition = asPoint(startPos)
			const oldEndPosition = asPoint(oldEndPos)
			const newEndPosition = asPoint(newEndPos)
			const delta = {startIndex, oldEndIndex, newEndIndex, startPosition, oldEndPosition, newEndPosition}
			old.edit(delta)
		}
		const t = parser.parse(edit.document.getText(), old) // TODO don't use getText, use Parser.Input
		trees[edit.document.uri.toString()] = t
	}
	function asPoint(pos: Position): Parser.Point {
		return {row: pos.line, column: pos.character}
	}
	function close(doc: TextDocument) {
		delete trees[doc.uri.toString()]
	}
	function colorUri(uri: Uri) {
		for (const editor of window.visibleTextEditors) {
			if (editor.document.uri == uri) {
				colorEditor(editor)
			}
		}
	}
	const warnedScopes = new Set<string>()
	function colorEditor(editor: TextEditor) {
		const t = trees[editor.document.uri.toString()]
		if (t == null) return
		if (editor.document.languageId != 'java') return;
		const scopes = colorJava(t.rootNode, visibleLines(editor))
		for (const scope of scopes.keys()) {
			const dec = decoration(scope)
			if (dec) {
				const ranges = scopes.get(scope)!.map(range)
				editor.setDecorations(dec, ranges)
			} else if (!warnedScopes.has(scope)) {
				console.warn(scope, 'was not found in the current theme')
				warnedScopes.add(scope)
			}
		}
	}
	async function colorAllOpen() {
		for (const editor of window.visibleTextEditors) {
			await open(editor)
		}
	}
	// Load active color theme
	async function onChangeConfiguration(event: ConfigurationChangeEvent) {
        let colorizationNeedsReload: boolean = event.affectsConfiguration('workbench.colorTheme')
			|| event.affectsConfiguration('editor.tokenColorCustomizations')
		if (colorizationNeedsReload) {
			await loadStyles()
			colorAllOpen()
		}
	}
    context.subscriptions.push(workspace.onDidChangeConfiguration(onChangeConfiguration))
	context.subscriptions.push(window.onDidChangeVisibleTextEditors(colorAllOpen))
	context.subscriptions.push(workspace.onDidChangeTextDocument(edit))
	context.subscriptions.push(workspace.onDidCloseTextDocument(close))
	context.subscriptions.push(window.onDidChangeTextEditorVisibleRanges(change => colorEditor(change.textEditor)))
	await loadStyles()
	await Parser.init()
	await colorAllOpen()
}

function range(x: ColorRange): Range {
	return new Range(x.start.row, x.start.column, x.end.row, x.end.column)
}

function visibleLines(editor: TextEditor) {
	return editor.visibleRanges.map(range => {
		const start = range.start.line
		const end = range.end.line
		return {start, end}
	})
}
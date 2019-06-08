import * as Parser from 'web-tree-sitter';

export function colorJava(root: Parser.SyntaxNode, visibleRanges: {start: number, end: number}[]): {[scope: string]: Parser.SyntaxNode[]} {
	const colors: {[scope: string]: Parser.SyntaxNode[]} = {
		'entity.name.type': [],
	};
	function scan(x: Parser.SyntaxNode) {
		if (!isVisible(x, visibleRanges)) return;
		switch (x.type) {
			case 'type_identifier':
				if (x.text != 'var') {
					colors['entity.name.type'].push(x);
				}
				break;
			case 'identifier': 
				switch (x.parent!.type) {
					case 'class_declaration':
					case 'type_parameter':
						colors['entity.name.type'].push(x);
					break;
				}
				break;
			// TODO stop early
		}
		for (const child of x.children) {
			scan(child)
		}
	}
	scan(root);
	// console.log(root.toString());
	return colors;
}

function isVisible(x: Parser.SyntaxNode, visibleRanges: {start: number, end: number}[]) {
	for (const {start, end} of visibleRanges) {
		const overlap = x.startPosition.row <= end + 1 && start - 1 <= x.endPosition.row;
		if (overlap) return true;
	}
	return false;
}

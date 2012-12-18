package de.fosd.typechef.crefactor.backend

import de.fosd.typechef.parser.c._
import de.fosd.typechef.crewrite.{ConditionalNavigation, ASTEnv, ASTNavigation}
import de.fosd.typechef.parser.c.Id
import scala.Some
import de.fosd.typechef.conditional.Opt


object ASTPosition extends ASTNavigation with ConditionalNavigation {

  /**
   * Retrieves all ids in the selected range.
   */
  def getSelectedIDs(ast: AST, file: String, startLine: Int, endLine: Int, startRow: Int, endRow: Int): List[Id] = {
    val result = filterASTElems[Id](ast).par.filter(x => isIdOfSelectionRange(x, startLine, endLine, startRow, endRow)).toList
    filterFileId[Id](result, file)
  }

  /**
   * Retrieves all ast elements of the selected range.
   */
  def getElementsInRange(ast: AST, astEnv: ASTEnv, file: String, startLine: Int, endLine: Int, startRow: Int, endRow: Int): List[AST] = {
    val result = astEnv.keys().par.filter(x => {
      x match {
        case key: AST => isElementOfSelectionRange(key, startLine, endLine, startRow, endRow)
        case _ => false
      }
    })
    filterFileId[AST](result.toList.asInstanceOf[List[AST]], file)
  }

  /**
   * Retrieves the selected statements or selected nested expression
   */
  def getSelectedExprOrStatement(ast: AST, astEnv: ASTEnv, file: String, startLine: Int, endLine: Int, startRow: Int, endRow: Int): List[AST] = {
    val statements = getSelectedStatements(ast, astEnv, file, startLine, endLine, startRow, endRow)
    val expressions = filterASTElems[Expr](statements).filter(expr => {
      var inExpr = false
      filterASTElems[Id](expr).foreach(id => if (isIdOfSelectionRange(id, startLine, endLine, startRow, endRow)) inExpr = true)
      inExpr
    })
    println("exür" + expressions)
    statements
  }

  /**
   * Retrieves all selected opts.
   */
  def getSelectedOpts(ast: AST, astEnv: ASTEnv, file: String, startLine: Int, endLine: Int, startRow: Int, endRow: Int): List[Opt[_]] = {
    var optElements = List[Opt[_]]()
    for (element <- getSelectedStatements(ast, astEnv, file, startLine, endLine, startRow, endRow)) {
      optElements = parentOpt(element, astEnv) :: optElements
    }
    optElements.reverse
  }

  /**
   * Retrieves all selected statements.
   */
  def getSelectedStatements(ast: AST, astEnv: ASTEnv, file: String, startLine: Int, endLine: Int, startRow: Int, endRow: Int): List[Statement] = {
    var statements = List[Statement]()

    /**
     * Work around to avoid duplicates, but also add statements with same hashvalues - only difference is their position
     */
    def addToList(statement: Statement, statements: List[Statement]): List[Statement] = {

      if (statements.contains(statement)) {
        /**
         * Check first if statements are the same - if so, check if position range is the same.
         */
        val sameStatements = statements.filter(x => (x.eq(statement) && x.range.eq(statement.range)))
        if (sameStatements.isEmpty) {
          statement :: statements
        } else {
          statements
        }
      } else {
        statement :: statements
      }
    }

    for (element <- getElementsInRange(ast, astEnv, file, startLine, endLine, startRow, endRow)) {
      if (element.isInstanceOf[Statement]) {
        statements = addToList(element.asInstanceOf[Statement], statements)
      } else {
        findPriorASTElem[Statement](element, astEnv) match {
          case Some(x) => statements = addToList(x, statements)
          case _ => return List[Statement]()

        }
      }
    }
    statements.toList.sortWith(comparePosition)
  }

  /**
   * Compares the position between two ast elements.
   */
  private def comparePosition(e1: AST, e2: AST) = (e1.getPositionFrom < e2.getPositionFrom)

  /**
   * Checks if an ast element is in a certain range.
   */
  private def isInRange(pos: Int, start: Int, end: Int) = ((start <= pos) && (pos <= end))

  private def isIdOfSelectionRange(id: Id, startLine: Int, endLine: Int, startRow: Int, endRow: Int): Boolean = {
    /**
     * Annotated ids have often the same starting line. As workaround we only identify the id by its end value.
     */
    (isInRange(id.getPositionTo.getLine, startLine, endLine) && ((endRow <= id.getPositionTo.getColumn) || (startRow <= id.getPositionTo.getColumn)))
  }

  /**
   * Retrieves if element is in the selection range.
   */
  private def isElementOfSelectionRange(element: AST, startLine: Int, endLine: Int, startRow: Int, endRow: Int): Boolean = {
    if (!((isInRange(element.getPositionFrom.getLine, startLine, endLine) && isInRange(element.getPositionTo.getLine, startLine, endLine)))) {
      return false
    } else if (element.getPositionFrom.getLine == startLine) {
      return isInRange(element.getPositionFrom.getColumn, startRow, endRow)
    } else if (element.getPositionTo.getLine == endLine) {
      return isInRange(element.getPositionTo.getColumn, startRow, endRow)
    }
    true
  }

  /**
   * Remove all ids except those from the specified file.
   */
  private def filterFileId[T <: AST](selection: List[T], file: String): List[T] = {
    // offset 5 because file path of id contains the string "file "
    val offset = 5
    selection.filter(p => p.getFile.get.regionMatches(true, offset, file, 0, file.length())).toList
  }
}
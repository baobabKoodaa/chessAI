
package Evaluators;

import Framework.Position;

public class DecompiledOurEvaluator extends Evaluator {
    static double[][] blackBoostTable = new double[][]{{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.15, 0.2, 0.2, 0.15, 0.0, 0.0}, {0.0, 0.0, 0.2, 0.3, 0.3, 0.2, 0.0, 0.0}, {0.0, 0.0, 0.15, 0.1, 0.1, 0.15, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};
    static double[][] whiteBoostTable = new double[][]{{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.15, 0.1, 0.1, 0.15, 0.0, 0.0}, {0.0, 0.0, 0.2, 0.3, 0.3, 0.2, 0.0, 0.0}, {0.0, 0.0, 0.15, 0.2, 0.2, 0.15, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};

    @Override
    public double eval(Position position) {
        int n = 0;
        int n2 = 0;
        if (6 == 6) {
            n = 1;
        }
        if (6 == 6) {
            n2 = 1;
        }
        double d = Math.random() - 0.5;
        for (int i = 0; i < position.board.length; ++i) {
            for (int j = 0; j < position.board[i].length; ++j) {
                if (position.board[i][j] == 0) continue;
                if (position.board[i][j] == 1) {
                    d += 1.0E9;
                }
                if (position.board[i][j] == 2) {
                    d += 9.0;
                }
                if (position.board[i][j] == 3) {
                    d += 5.25;
                }
                if (position.board[i][j] == 4) {
                    d += 3.25;
                }
                if (position.board[i][j] == 5) {
                    d += 3.0;
                }
                if (position.board[i][j] == 6) {
                    d += 1.0;
                }
                if (position.board[i][j] == 7) {
                    d -= 1.0E9;
                }
                if (position.board[i][j] == 8) {
                    d -= 8.5 - 0.05 * (double)j;
                }
                if (position.board[i][j] == 9) {
                    d -= 4.75 - 0.05 * (double)j;
                }
                if (position.board[i][j] == 10) {
                    d -= 2.75 - 0.05 * (double)j;
                }
                if (position.board[i][j] == 11) {
                    d -= 2.5 - 0.05 * (double)j;
                }
                if (position.board[i][j] == 12) {
                    d -= 1.0 - 0.1 * (double)j;
                }
                if (Position.isWhitePiece((int)position.board[i][j])) {
                    d += whiteBoostTable[j + n2][i + n];
                    continue;
                }
                if (!Position.isBlackPiece((int)position.board[i][j])) continue;
                d -= blackBoostTable[j + n2][i + n];
            }
        }
        return d;
    }

}

import GUI.MainFrame;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;
public class Main {
	
	static Evaluator ce; // current evaluator
	static Evaluator oe; // our evaluator
	static Evaluator ye; // your evaluator
	
	
	class PositionComparator implements Comparator<Position> {
		public int compare(Position p1, Position p2) {
			return Double.compare(eval(p1), eval(p2));
		}
	}
        
        static double eval(Position p) {
            if (!Double.isNaN(p.cachedResult))
                return p.cachedResult;
            double d = 0.0;
            if (ce == ye)
                d = ce.eval(p);
            else
            {
                Position mirrored = p.mirror();
                d = -ce.eval(mirrored);
            }
            p.cachedResult = d;
            return d;
        }
	
	static double alphabeta(Position p, int depth, double alpha, double beta, int player) {
            // 0 tries to maximize, 1 tries to minimize
	    if (p.winner == -1) return -1E10-depth; // prefer to win sooner
	    if (p.winner == +1) return +1E10+depth; // and lose later
				    
		if(depth == 0) return ce.eval(p);
		Vector<Position> P = p.getNextPositions();
		Collections.sort(P, (new Main()).new PositionComparator());
		if(player == 0) Collections.reverse(P);
		
		if(player == 0) {
			for(int i = 0; i < P.size(); ++i) {
				alpha = Math.max(alpha, alphabeta(P.elementAt(i),depth-1,alpha,beta,1));
				if(beta <= alpha) break;
			}
			return alpha;
		}
		
		for(int i = 0; i < P.size(); ++i) {
			beta = Math.min(beta,alphabeta(P.elementAt(i),depth-1,alpha,beta,0));
			if(beta <= alpha) break;
		}
		
		return beta;
	}
	
	static double minmax(Position p, int depth, int player) {
		double alpha = -Double.MAX_VALUE, beta = Double.MAX_VALUE;
		return alphabeta(p,depth,alpha,beta,player);
	}
	
	public static void main(String[] args) {
                MainFrame frame = new MainFrame();
                
		//oe = new OurEvaluator(); // <-- defaultAI
		//ye = new YourEvaluator();
                
                oe = new OurEvaluator(); // <-- defaultAI
                ye = new YourEvaluator();
                //ye = new FoodChainV1plusHash();
		
		int depth = 5;
		

                
                double whiteTimeTotal = 0;
                double blackTimeTotal = 0;
                int whiteWins = 0;
                int blackWins = 0;
                int ties = 0;
		
		
		

		
                while (true) {
                    
                    Position p = new Position();
                    p.setStartingPosition();
                    p.print(frame.c);
                    oe.eval(p);
                    
                    long ms = System.currentTimeMillis();
                    int moveNumber = 0;
                    for (; moveNumber < 200; moveNumber++) {
                            ce = oe;
                            ce = ye;
                            Vector<Position> P = p.getNextPositions();

                            double evaluation = (new OurEvaluator()).eval(p);

                            if(p.winner == +1) {
                                    System.out.println("White won.");
                                    frame.c.gameOver("White won.");
                                    whiteWins++;
                                    break;
                            } 

                            if(p.winner == -1) {
                                    System.out.println("Black won.");
                                    frame.c.gameOver("Black won.");
                                    blackWins++;
                                    break;
                            }

                            if(P.size() == 0) {
                                    System.out.println("No more available moves");
                                    frame.c.gameOver("No more available moves");
                                    ties++;
                                    break;
                            }

                            Position bestPosition = new Position();
                            if(p.whiteToMove) {
                                    ce = ye;
                                    double max = -1./0.;
                                    for(int i = 0; i < P.size(); ++i) {
                                            double val = minmax(P.elementAt(i),depth,1);
                                            if(max < val) {
                                                    bestPosition = P.elementAt(i);
                                                    max = val;
                                            }
                                    }
                            } else {
                                    ce = oe;
                                    double min = 1./0.;
                                    for(int i = 0; i < P.size(); ++i) {
                                            double val = minmax(P.elementAt(i),depth,0);
                                            if(min > val) {
                                                    bestPosition = P.elementAt(i);
                                                    min = val;
                                            }
                                    }
                            }

                            assert p.whiteToMove != bestPosition.whiteToMove;
                            p = bestPosition;
                            p.print(frame.c);

                            long curtime = System.currentTimeMillis();

                            double timespent = (curtime-ms)/1000.0;
                            if (p.whiteToMove) {
                                blackTimeTotal += timespent;
                            } else {
                                whiteTimeTotal += timespent;
                            }
                            //System.out.println((p.whiteToMove ? "Black" : "White") +" move took "+timespent+" seconds");
                            //System.out.println("     Overall white has spent " + whiteTimeTotal + "s, black has spent " + blackTimeTotal + "s");
                            ms = curtime;
                    }
                    if (moveNumber == 200) {
                        System.out.println("TIE - after 200 moves");
                        ties++;
                    }
                    System.out.println("Tally :: WHITE " + whiteWins + " :: BLACK " + blackWins + " :: TIES " + ties);
                    System.out.println("Time :: white " + whiteTimeTotal + " :: black " + blackTimeTotal);
                }
                
	}
}

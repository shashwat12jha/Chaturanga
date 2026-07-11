import { useState, useEffect, useRef } from 'react';
import { Chess } from 'chess.js';
import { Chessboard } from 'react-chessboard';
import './App.css';

function App() {
  const [game, setGame] = useState(new Chess());
  const [engineEval, setEngineEval] = useState('');
  const [wsConnected, setWsConnected] = useState(false);
  const wsRef = useRef(null);

  useEffect(() => {
    const ws = new WebSocket('ws://localhost:8080');
    wsRef.current = ws;

    ws.onopen = () => {
      if (wsRef.current === ws) {
        setWsConnected(true);
        ws.send('uci');
        ws.send('isready');
        // Let's also start a new game on the engine
        ws.send('ucinewgame');
        ws.send(`position fen ${game.fen()}`);
      }
    };

    ws.onmessage = (event) => {
      const msg = event.data;
      console.log('Engine says:', msg);

      if (msg.startsWith('info') && msg.includes('score cp')) {
        const match = msg.match(/score cp (-?\d+)/);
        if (match) {
          const evalVal = parseInt(match[1], 10) / 100;
          setEngineEval(evalVal > 0 ? `+${evalVal.toFixed(2)}` : evalVal.toFixed(2));
        }
      } else if (msg.startsWith('info') && msg.includes('score mate')) {
         const match = msg.match(/score mate (-?\d+)/);
         if (match) {
           setEngineEval(`M${match[1]}`);
         }
      }

      if (msg.startsWith('bestmove')) {
        const bestMove = msg.split(' ')[1];
        if (bestMove && bestMove !== '(none)') {
          makeEngineMove(bestMove);
        }
      }
    };

    ws.onclose = () => {
      if (wsRef.current === ws) {
        setWsConnected(false);
      }
    };

    return () => {
      ws.close();
    };
  }, []); // Note: leaving game out of deps because we only want one ws connection

  const makeEngineMove = (moveStr) => {
    const sourceSquare = moveStr.substring(0, 2);
    const targetSquare = moveStr.substring(2, 4);
    const promotion = moveStr.length > 4 ? moveStr[4] : undefined;
    
    setGame((prevGame) => {
      const gameCopy = new Chess(prevGame.fen());
      try {
        const moveObj = { from: sourceSquare, to: targetSquare };
        if (promotion) moveObj.promotion = promotion;
        gameCopy.move(moveObj);
      } catch (e) {
        console.error('Invalid move from engine:', moveStr, e);
      }
      return gameCopy;
    });
  };

  const askEngine = (currentFen) => {
    if (wsRef.current && wsConnected) {
      console.log('Sending fen to engine:', currentFen);
      wsRef.current.send(`position fen ${currentFen}`);
      wsRef.current.send('go depth 10');
    } else {
      console.log('Cannot ask engine, wsConnected:', wsConnected);
    }
  };

  function onDrop(sourceSquare, targetSquare) {
    console.log('onDrop:', sourceSquare, targetSquare);
    let move = null;
    const gameCopy = new Chess(game.fen());
    try {
      // only add promotion if it's a pawn moving to the 8th or 1st rank
      const isPromotion = (sourceSquare.charAt(1) === '7' && targetSquare.charAt(1) === '8') ||
                          (sourceSquare.charAt(1) === '2' && targetSquare.charAt(1) === '1');
      
      const moveObj = { from: sourceSquare, to: targetSquare };
      if (isPromotion) moveObj.promotion = 'q';
      
      move = gameCopy.move(moveObj);
    } catch (e) {
      console.log('Move error:', e);
      return false; 
    }

    if (move === null) {
      console.log('Move was null');
      return false;
    }

    setGame(gameCopy);
    
    askEngine(gameCopy.fen());
    return true;
  }

  return (
    <div className="app-container">
      <header className="app-header">
        <h1>Chaturanga</h1>
        <div className={`status-badge ${wsConnected ? 'connected' : 'disconnected'}`}>
          {wsConnected ? 'Engine Connected' : 'Engine Disconnected'}
        </div>
      </header>

      <main className="main-content">
        <div className="board-container">
          <Chessboard 
            position={game.fen()} 
            onPieceDrop={onDrop}
            customBoardStyle={{
              borderRadius: '8px',
              boxShadow: '0 10px 30px rgba(0, 0, 0, 0.5)'
            }}
            customDarkSquareStyle={{ backgroundColor: '#2d3748' }}
            customLightSquareStyle={{ backgroundColor: '#e2e8f0' }}
          />
        </div>

        <div className="sidebar">
          <div className="panel stats-panel">
            <h2>Engine Evaluation</h2>
            <div className="eval-value">{engineEval || '0.00'}</div>
          </div>
          
          <div className="panel controls-panel">
             <button className="btn primary" onClick={() => {
                const newGame = new Chess();
                setGame(newGame);
                setEngineEval('');
                if (wsRef.current && wsConnected) {
                   wsRef.current.send('ucinewgame');
                   wsRef.current.send(`position fen ${newGame.fen()}`);
                }
             }}>New Game</button>
             <button className="btn secondary" onClick={() => {
                if (game.history().length > 0) {
                    const gameCopy = new Chess(game.fen());
                    gameCopy.undo();
                    gameCopy.undo(); // Undo engine move as well
                    setGame(gameCopy);
                    askEngine(gameCopy.fen());
                }
             }}>Undo Move</button>
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;

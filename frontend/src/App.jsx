import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import AppLayout from './components/Layout';
import AppRoutes from './routes';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <AppLayout>
        <AppRoutes />
      </AppLayout>
    </BrowserRouter>
  );
}

export default App;

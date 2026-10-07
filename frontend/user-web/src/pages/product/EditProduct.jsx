import React from 'react';
import { useParams } from 'react-router-dom';
import ProductForm from './ProductForm.jsx';

/**
 * 编辑商品页面
 */
const EditProduct = () => {
  const { id } = useParams();
  return <ProductForm mode="edit" productId={id} />;
};

export default EditProduct;
